package com.ecodispatch.controller;

import com.ecodispatch.model.DistrictAlert;
import com.ecodispatch.repository.DistrictAlertRepository;
import com.ecodispatch.service.WatsonxAiAgentService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ClimateAlertController — REST API + Automated Dispatch Scheduler.
 *
 * <p>Exposes the active anomaly feed to the Leaflet.js dashboard map and
 * runs the automated heat-scan dispatch loop every 15 seconds, simulating
 * the scheduled 6 AM regional data sweep described in the project brief.</p>
 *
 * <ul>
 *   <li>GET /api/alerts — Returns all non-NORMAL alerts for map rendering.</li>
 *   <li>GET /api/alerts/all — Returns every district record (admin/debug view).</li>
 *   <li>@Scheduled — Simulates daily batch: scan → Granite → status update → log.</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")   // Allow Leaflet JS fetch() from the same origin or dev server
public class ClimateAlertController {

    private final DistrictAlertRepository alertRepository;
    private final WatsonxAiAgentService   watsonxService;

    // ─────────────────────────────────────────────────────────────────────────
    //  Seed Data — loaded once at startup
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Pre-populates the H2 database with representative Tamil Nadu district
     * zones so the dashboard renders markers on first load.
     * All zones start with NORMAL status; the scheduler updates them.
     */
    @PostConstruct
    public void seedDatabase() {
        if (alertRepository.count() > 0) return;   // idempotent

        List<DistrictAlert> zones = List.of(
            // ── Chennai District ──────────────────────────────────────────────
            DistrictAlert.builder()
                .districtName("Chennai").zoneName("Saidapet")
                .temperature(39.5).canopyPercentage(8.2)
                .latitude(13.0202).longitude(80.2210)
                .status("NORMAL").aiActionPlan("Baseline — no action required.")
                .build(),
            DistrictAlert.builder()
                .districtName("Chennai").zoneName("Anna Nagar")
                .temperature(37.8).canopyPercentage(14.5)
                .latitude(13.0850).longitude(80.2101)
                .status("NORMAL").aiActionPlan("Baseline — no action required.")
                .build(),
            DistrictAlert.builder()
                .districtName("Chennai").zoneName("Velachery")
                .temperature(40.3).canopyPercentage(6.9)
                .latitude(12.9815).longitude(80.2180)
                .status("NORMAL").aiActionPlan("Baseline — no action required.")
                .build(),
            // ── Coimbatore District ───────────────────────────────────────────
            DistrictAlert.builder()
                .districtName("Coimbatore").zoneName("Ukkadam")
                .temperature(41.1).canopyPercentage(5.5)
                .latitude(10.9847).longitude(76.9640)
                .status("NORMAL").aiActionPlan("Baseline — no action required.")
                .build(),
            DistrictAlert.builder()
                .districtName("Coimbatore").zoneName("Gandhipuram")
                .temperature(38.6).canopyPercentage(11.3)
                .latitude(11.0013).longitude(76.9730)
                .status("NORMAL").aiActionPlan("Baseline — no action required.")
                .build(),
            // ── Madurai District ──────────────────────────────────────────────
            DistrictAlert.builder()
                .districtName("Madurai").zoneName("Tallakulam")
                .temperature(42.0).canopyPercentage(4.1)
                .latitude(9.9195).longitude(78.1193)
                .status("NORMAL").aiActionPlan("Baseline — no action required.")
                .build(),
            DistrictAlert.builder()
                .districtName("Madurai").zoneName("Anaiyur")
                .temperature(36.4).canopyPercentage(18.7)
                .latitude(9.9000).longitude(78.0800)
                .status("NORMAL").aiActionPlan("Baseline — no action required.")
                .build(),
            // ── Salem District ────────────────────────────────────────────────
            DistrictAlert.builder()
                .districtName("Salem").zoneName("Shevapet")
                .temperature(39.9).canopyPercentage(9.4)
                .latitude(11.6643).longitude(78.1460)
                .status("NORMAL").aiActionPlan("Baseline — no action required.")
                .build(),
            // ── Trichy District ───────────────────────────────────────────────
            DistrictAlert.builder()
                .districtName("Trichy").zoneName("Srirangam")
                .temperature(38.2).canopyPercentage(12.8)
                .latitude(10.8625).longitude(78.6890)
                .status("NORMAL").aiActionPlan("Baseline — no action required.")
                .build(),
            // ── Tirunelveli District ──────────────────────────────────────────
            DistrictAlert.builder()
                .districtName("Tirunelveli").zoneName("Palayamkottai")
                .temperature(40.7).canopyPercentage(7.6)
                .latitude(8.7139).longitude(77.7567)
                .status("NORMAL").aiActionPlan("Baseline — no action required.")
                .build()
        );

        alertRepository.saveAll(zones);
        log.info("Database seeded with {} district zones.", zones.size());
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  REST Endpoints
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns all active anomaly alerts (status != NORMAL) for the map dashboard.
     * The Leaflet.js frontend polls this endpoint every 15 seconds.
     *
     * @return JSON array of {@link DistrictAlert} records with active status
     */
    @GetMapping
    public ResponseEntity<List<DistrictAlert>> getActiveAlerts() {
        List<DistrictAlert> activeAlerts = alertRepository.findByStatusNot("NORMAL");
        log.debug("Dashboard polling /api/alerts — returning {} active alerts.", activeAlerts.size());
        return ResponseEntity.ok(activeAlerts);
    }

    /**
     * Returns every district zone record regardless of status.
     * Intended for admin inspection and the H2 console.
     */
    @GetMapping("/all")
    public ResponseEntity<List<DistrictAlert>> getAllAlerts() {
        return ResponseEntity.ok(alertRepository.findAll());
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Automated Dispatch Scheduler — simulates the 6 AM daily scan loop
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Automated heat-anomaly scan and dispatch loop.
     *
     * <p>Runs every 15 seconds (fixedRate = 15000 ms) to simulate the
     * real-world scheduled 6 AM regional heat-file sweep. Each execution:</p>
     * <ol>
     *   <li>Iterates mock weather data representing fresh sensor readings.</li>
     *   <li>Invokes {@link WatsonxAiAgentService#evaluateZoneMetrics} for each zone.</li>
     *   <li>Updates the corresponding {@link DistrictAlert} status in H2.</li>
     *   <li>Logs the Granite model's dispatch recommendation to the terminal.</li>
     * </ol>
     */
    @Scheduled(fixedRate = 15_000)
    public void runDispatchScanCycle() {
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("EcoDispatch Scheduler — Heat Anomaly Scan Cycle STARTED");
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        // ── Mock incoming weather sensor readings (simulates fresh file ingestion)
        // Format: { districtName, zoneName, temperature°C, canopy% }
        double[][] mockReadings = {
            // [ temperature, canopy ]  — ordered to match seeded zones by name
            { 41.2,  7.8 },   // Chennai     / Saidapet       — CRITICAL
            { 36.5, 14.5 },   // Chennai     / Anna Nagar     — NORMAL
            { 42.5,  6.1 },   // Chennai     / Velachery      — CRITICAL
            { 43.0,  4.8 },   // Coimbatore  / Ukkadam        — CRITICAL
            { 37.2, 11.3 },   // Coimbatore  / Gandhipuram    — ELEVATED
            { 44.1,  3.9 },   // Madurai     / Tallakulam     — CRITICAL
            { 35.8, 19.2 },   // Madurai     / Anaiyur        — NORMAL
            { 40.1,  9.0 },   // Salem       / Shevapet       — CRITICAL
            { 38.9, 12.0 },   // Trichy      / Srirangam      — ELEVATED
            { 41.6,  7.2 },   // Tirunelveli / Palayamkottai  — CRITICAL
        };

        List<DistrictAlert> allZones = alertRepository.findAll();

        if (allZones.size() != mockReadings.length) {
            log.warn("Zone count mismatch — seeded zones: {} | mock readings: {}",
                     allZones.size(), mockReadings.length);
        }

        int criticalCount = 0;
        int elevatedCount = 0;

        for (int i = 0; i < Math.min(allZones.size(), mockReadings.length); i++) {
            DistrictAlert zone  = allZones.get(i);
            double temp         = mockReadings[i][0];
            double canopy       = mockReadings[i][1];

            // ── Update entity with latest readings ────────────────────────────
            zone.setTemperature(temp);
            zone.setCanopyPercentage(canopy);

            // ── Invoke IBM Granite agent ──────────────────────────────────────
            String aiPlan = watsonxService.evaluateZoneMetrics(
                zone.getDistrictName(), zone.getZoneName(), temp, canopy);

            zone.setAiActionPlan(aiPlan);

            // ── Classify status from temperature threshold ────────────────────
            String newStatus;
            if (temp > 40.0) {
                newStatus = "ALERT_PENDING_WATERING";
                criticalCount++;
            } else if (temp > 37.0) {
                newStatus = "ALERT_PENDING_WATERING";
                elevatedCount++;
            } else {
                newStatus = "NORMAL";
            }
            zone.setStatus(newStatus);
            alertRepository.save(zone);

            // ── Terminal dispatch log ─────────────────────────────────────────
            log.info("▶ [{}/{}] {}°C | Canopy {}% | Status: {} ",
                     zone.getDistrictName(), zone.getZoneName(),
                     temp, canopy, newStatus);
            log.info("  ✦ Granite Dispatch: {}", aiPlan);
        }

        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("Scan complete — CRITICAL zones: {} | ELEVATED zones: {} | NORMAL: {}",
                 criticalCount, elevatedCount,
                 allZones.size() - criticalCount - elevatedCount);
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }
}
