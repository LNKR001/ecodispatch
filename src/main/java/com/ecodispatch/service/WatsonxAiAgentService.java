package com.ecodispatch.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.util.*;

/**
 * WatsonxAiAgentService — IBM Granite model invocation service.
 *
 * <p>This service builds and dispatches a direct REST payload to the
 * IBM watsonx.ai text-generation endpoint using the
 * {@code ibm/granite-3-0-8b-instruct} foundation model.</p>
 *
 * <p><strong>System Role (immutable):</strong><br>
 * "You are the State-Level Climate Intelligence Dispatcher. Evaluate incoming
 * district metrics. If any zone deviates from baseline or hits >40°C, flag it
 * as a CRITICAL_ANOMALY and return an actionable plain-language text instruction
 * for city watering and planting ground crews."</p>
 *
 * <p><strong>Credential detection:</strong> If {@code ibm.watsonx.api-key} is
 * empty, blank, or begins with {@code "YOUR_"}, no network call is attempted and
 * the service immediately returns a deterministic local mock response.
 * The application therefore runs fully on localhost out-of-the-box.</p>
 */
@Slf4j
@Service
public class WatsonxAiAgentService {

    // ── System-level role prompt (immutable per project specification) ────────
    private static final String SYSTEM_ROLE =
        "You are the State-Level Climate Intelligence Dispatcher. " +
        "Evaluate incoming district metrics. " +
        "If any zone deviates from baseline or hits >40°C, flag it as a CRITICAL_ANOMALY " +
        "and return an actionable plain-language text instruction for city watering and " +
        "planting ground crews.";

    // ── Injected watsonx.ai properties from application.properties ────────────
    @Value("${ibm.watsonx.api-key}")
    private String apiKey;

    @Value("${ibm.watsonx.project-id}")
    private String projectId;

    @Value("${ibm.watsonx.model-id}")
    private String modelId;

    @Value("${ibm.watsonx.url}")
    private String watsonxUrl;

    @Value("${ibm.watsonx.iam-url}")
    private String iamUrl;

    @Value("${ibm.watsonx.max-new-tokens:300}")
    private int maxNewTokens;

    @Value("${ibm.watsonx.decoding-method:greedy}")
    private String decodingMethod;

    private final RestTemplate restTemplate = new RestTemplate();

    // ─────────────────────────────────────────────────────────────────────────
    //  Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} when the configured API key is absent or still holds
     * a placeholder value — any of:
     * <ul>
     *   <li>null / empty / blank</li>
     *   <li>starts with {@code "YOUR_"} (covers all placeholder variants)</li>
     * </ul>
     * No network call is ever attempted when this method returns {@code true}.
     */
    private boolean isCredentialsMissing() {
        return apiKey == null
            || apiKey.isBlank()
            || apiKey.toUpperCase().startsWith("YOUR_");
    }

    /**
     * Evaluates a district's climate metrics using the IBM Granite model agent.
     *
     * <p>If {@link #isCredentialsMissing()} is {@code true} the method short-circuits
     * immediately to {@link #buildMockResponse} — no TCP connection is opened, so
     * the application runs on localhost without any hang or timeout.</p>
     *
     * <p>When real credentials are present the method calls the live watsonx.ai
     * endpoint and falls back to the mock only on a network or API error.</p>
     *
     * @param districtName     parent district (e.g., "Chennai")
     * @param zoneName         sub-zone locality (e.g., "Saidapet")
     * @param temperature      latest temperature reading in °C
     * @param canopyPercentage urban tree canopy coverage percentage
     * @return plain-language dispatch instruction from the Granite model
     */
    public String evaluateZoneMetrics(String districtName,
                                      String zoneName,
                                      double temperature,
                                      double canopyPercentage) {

        log.debug("Granite Agent — evaluating zone: {}/{} | temp={}°C canopy={}%",
                  districtName, zoneName, temperature, canopyPercentage);

        // ── Short-circuit: skip all network I/O when credentials are absent ───
        if (isCredentialsMissing()) {
            log.info("[LOCAL MODE] No valid watsonx.ai credentials — returning mock Granite response for {}/{}",
                     districtName, zoneName);
            return buildMockResponse(districtName, zoneName, temperature, canopyPercentage);
        }

        // ── Live path: call IBM Granite via watsonx.ai REST endpoint ──────────
        try {
            String bearerToken = fetchIamBearerToken();
            String prompt      = buildPrompt(districtName, zoneName, temperature, canopyPercentage);
            return callGenerationEndpoint(bearerToken, prompt);
        } catch (Exception ex) {
            log.error("Granite API call failed for {}/{}: {} — falling back to mock",
                      districtName, zoneName, ex.getMessage());
            return buildMockResponse(districtName, zoneName, temperature, canopyPercentage);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  IAM Token Exchange
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Exchanges the IBM Cloud API key for a short-lived IAM Bearer token
     * using the standard IBM IAM token endpoint.
     */
    @SuppressWarnings("unchecked")
    private String fetchIamBearerToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        String body = "grant_type=urn:ibm:params:oauth:grant-type:apikey&apikey=" + apiKey;
        HttpEntity<String> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response =
            restTemplate.exchange(iamUrl, HttpMethod.POST, request, Map.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            String token = (String) response.getBody().get("access_token");
            log.debug("IBM IAM token acquired successfully.");
            return token;
        }
        throw new RuntimeException("Failed to acquire IBM IAM token — HTTP " + response.getStatusCode());
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Prompt Construction
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Assembles the full generation prompt by prepending the system role
     * to a structured user metric block.
     */
    private String buildPrompt(String districtName,
                                String zoneName,
                                double temperature,
                                double canopyPercentage) {
        return SYSTEM_ROLE + "\n\n" +
               "=== INCOMING ZONE METRICS ===\n" +
               "District      : " + districtName      + "\n" +
               "Zone          : " + zoneName          + "\n" +
               "Temperature   : " + temperature       + "°C\n" +
               "Canopy Cover  : " + canopyPercentage  + "%\n" +
               "Baseline Temp : 35.0°C\n" +
               "===========================\n\n" +
               "Provide a single, concise dispatch instruction for ground crews:";
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  REST Generation Call
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Posts the generation payload to the IBM watsonx.ai endpoint and
     * extracts the generated text from the response body.
     */
    @SuppressWarnings("unchecked")
    private String callGenerationEndpoint(String bearerToken, String prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(bearerToken);

        // Build the watsonx.ai generation request body
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("decoding_method", decodingMethod);
        parameters.put("max_new_tokens",  maxNewTokens);
        parameters.put("stop_sequences",  List.of("\n\n"));

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model_id",   modelId);
        requestBody.put("project_id", projectId);
        requestBody.put("input",      prompt);
        requestBody.put("parameters", parameters);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<Map> response =
            restTemplate.exchange(watsonxUrl, HttpMethod.POST, entity, Map.class);

        // Navigate: results[0].generated_text
        if (response.getBody() != null) {
            List<Map<String, Object>> results =
                (List<Map<String, Object>>) response.getBody().get("results");
            if (results != null && !results.isEmpty()) {
                String generated = (String) results.get(0).get("generated_text");
                log.debug("Granite response received ({} chars).", generated.length());
                return generated.trim();
            }
        }
        throw new RuntimeException("Unexpected empty response body from watsonx.ai endpoint.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Development Mock
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Generates a deterministic, severity-appropriate mock response for use
     * during local development when IBM credentials are not yet configured.
     */
    private String buildMockResponse(String districtName,
                                     String zoneName,
                                     double temperature,
                                     double canopyPercentage) {
        if (temperature > 40.0) {
            return String.format(
                "[CRITICAL_ANOMALY - %s / %s] Temperature %.1f C exceeds critical threshold. " +
                "Deploy watering tanker immediately to alleviate local soil moisture crisis. " +
                "Coordinate with Parks Division to initiate emergency native-species planting " +
                "within 0.5 km radius. Canopy cover at %.1f%% - priority reforestation required. " +
                "Dispatch green crew to site within 2 hours.",
                districtName, zoneName, temperature, canopyPercentage);
        } else if (temperature > 37.0) {
            return String.format(
                "[ELEVATED_ALERT - %s / %s] Temperature %.1f C deviates from 35 C baseline. " +
                "Schedule supplemental irrigation within 24 hours. " +
                "Flag zone for canopy audit (current coverage: %.1f%%). " +
                "Recommend medium-density shrub planting along road corridors.",
                districtName, zoneName, temperature, canopyPercentage);
        } else {
            return String.format(
                "[NORMAL - %s / %s] Temperature %.1f C within acceptable baseline range. " +
                "Routine canopy maintenance schedule applies. No emergency dispatch required.",
                districtName, zoneName, temperature, canopyPercentage);
        }
    }
}
