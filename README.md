# State-Wide EcoDispatch
### A Gen-AI Powered Regional Heat Anomaly Tracking & Greenery Restoration Platform
> **1M1B AI for Sustainability Virtual Internship** — AICTE / IBM Partnership Project

[![SDG 11](https://img.shields.io/badge/SDG%2011-Sustainable%20Cities-green)](https://sdgs.un.org/goals/goal11)
[![SDG 13](https://img.shields.io/badge/SDG%2013-Climate%20Action-orange)](https://sdgs.un.org/goals/goal13)
[![IBM Granite](https://img.shields.io/badge/IBM%20Granite-3.0%208B%20Instruct-blue)](https://www.ibm.com/products/watsonx-ai)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-brightgreen)](https://spring.io/projects/spring-boot)

---

## Overview

EcoDispatch is an event-driven Java Spring Boot platform that uses an **IBM Granite model agent**
to automatically scan regional heat data, detect temperature anomalies, and push real-time
dispatch instructions to a **municipal field crew dashboard map** built with Leaflet.js.

The system targets Tamil Nadu's urban heat-island problem — scanning district zones like
**Saidapet (Chennai)**, **Ukkadam (Coimbatore)**, and **Tallakulam (Madurai)** — and
generates plain-language restoration commands for city watering and planting ground crews.

---

## Project Structure

```
ecodispatch/
├── pom.xml                                             # Maven build descriptor
└── src/main/
    ├── java/com/ecodispatch/
    │   ├── EcoDispatchApplication.java                 # Spring Boot entry point
    │   ├── model/
    │   │   └── DistrictAlert.java                      # JPA Entity — climate zone record
    │   ├── repository/
    │   │   └── DistrictAlertRepository.java            # Spring Data JPA repository
    │   ├── service/
    │   │   └── WatsonxAiAgentService.java              # IBM Granite model REST invocation
    │   └── controller/
    │       └── ClimateAlertController.java             # REST API + @Scheduled dispatch loop
    └── resources/
        ├── application.properties                      # H2 + watsonx.ai configuration
        └── static/
            └── index.html                              # Leaflet.js map dashboard
```

---

## Architecture — Automated Dispatch Loop

```
Heat Sensor Data
      │
      ▼
@Scheduled (every 15s)  ──────────────────────────────────────────────┐
      │                                                                │
      ▼                                                                │
WatsonxAiAgentService                                                  │
  └─ IBM IAM Token Exchange                                            │
  └─ Granite-3-0-8B-Instruct Prompt:                                   │
       "You are the State-Level Climate Intelligence Dispatcher…"      │
  └─ Returns: plain-language dispatch instruction                      │
      │                                                                │
      ▼                                                                │
DistrictAlert (H2 DB)                                                  │
  status: NORMAL → ALERT_PENDING_WATERING → DISPATCHED                 │
      │                                                                │
      ▼                                                                │
GET /api/alerts ──► Leaflet.js Dashboard                               │
  Blinking red markers at anomaly coordinates                          │
  Popup: AI-generated action plan                                      │
      │                                                                │
      └──────────────────────── 15s poll ───────────────────────────────┘
```

---

## Prerequisites

| Tool | Version |
|------|---------|
| Java | 17+ |
| Maven | 3.9+ |
| (Optional) IBM Cloud Account | For live Granite API |

---

## Quick Start — Local Development

```bash
# 1. Clone the repository
git clone https://github.com/<your-username>/ecodispatch.git
cd ecodispatch

# 2. Build the project
mvn clean package -DskipTests

# 3. Run
mvn spring-boot:run
```

Open **http://localhost:8080** in your browser.

> **No IBM credentials needed for local testing.** When `ibm.watsonx.api-key` is set to
> its placeholder value, the service automatically uses deterministic mock Granite responses
> so you can fully test the dispatch loop and map dashboard offline.

---

## Connecting to IBM watsonx.ai (Live Mode)

1. Create a project in [IBM watsonx.ai](https://www.ibm.com/products/watsonx-ai).
2. Generate an IBM Cloud API key in **IAM → API keys**.
3. Copy your **Project ID** from the watsonx.ai project settings.
4. Update `src/main/resources/application.properties`:

```properties
ibm.watsonx.api-key=<your-real-api-key>
ibm.watsonx.project-id=<your-project-id>
```

> **Security:** Never commit real API keys. Use environment variable overrides or
> IBM Secrets Manager. Spring Boot picks up `SPRING_APPLICATION_JSON` or
> `--ibm.watsonx.api-key=...` CLI args without touching the properties file.

---

## API Reference

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/alerts` | Returns all **active anomaly** alerts (non-NORMAL) |
| GET | `/api/alerts/all` | Returns **all** district zone records |
| GET | `/h2-console` | H2 in-memory database browser (dev only) |

### Sample Response — `/api/alerts`

```json
[
  {
    "id": 1,
    "districtName": "Chennai",
    "zoneName": "Saidapet",
    "temperature": 41.2,
    "canopyPercentage": 7.8,
    "latitude": 13.0202,
    "longitude": 80.221,
    "status": "ALERT_PENDING_WATERING",
    "aiActionPlan": "[CRITICAL_ANOMALY — Chennai / Saidapet] Temperature 41.2°C exceeds critical threshold. Deploy watering tanker immediately to alleviate local soil moisture crisis..."
  }
]
```

---

## SDG Alignment

| SDG | Goal | EcoDispatch Contribution |
|-----|------|--------------------------|
| **SDG 11** | Sustainable Cities & Communities | Reduces urban heat-island impact via targeted greenery dispatch |
| **SDG 13** | Climate Action | Automates detection and escalation of regional climate stress events |

---

## Responsible AI

- **Fairness:** Anomaly scoring is weighted against data density to prevent underserved
  districts with sparse sensor coverage from being systematically de-prioritised.
- **Privacy:** No PII is collected. Field crew location data is session-scoped and anonymised.
  All watsonx.ai API traffic uses HTTPS with IAM token-based authentication.

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 17, Spring Boot 3.2, Spring Data JPA |
| AI Engine | IBM Granite 3.0 8B Instruct via watsonx.ai REST API |
| Database | H2 In-Memory (dev) — swap datasource URL for PostgreSQL/MySQL in prod |
| Frontend | HTML5, Bootstrap 5, Leaflet.js, CartoDB Dark Matter tiles |
| Build | Apache Maven 3.9 |

---

*1M1B AI for Sustainability Internship — AICTE / IBM Partnership*
