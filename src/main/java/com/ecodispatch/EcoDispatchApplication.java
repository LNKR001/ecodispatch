package com.ecodispatch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * EcoDispatchApplication — Spring Boot entry point.
 *
 * <p>@EnableScheduling activates the @Scheduled simulation loop in
 * {@link com.ecodispatch.controller.ClimateAlertController} that
 * mimics the automated 6 AM regional heat-scan dispatch cycle.</p>
 *
 * 1M1B AI for Sustainability Internship Project
 * Project: State-Wide EcoDispatch
 */
@Slf4j
@SpringBootApplication
@EnableScheduling
public class EcoDispatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(EcoDispatchApplication.class, args);
        log.info("╔══════════════════════════════════════════════════════════════╗");
        log.info("║   State-Wide EcoDispatch — Heat Anomaly Dispatch Platform    ║");
        log.info("║   IBM Granite Agent Scheduler: ACTIVE (every 15 seconds)     ║");
        log.info("║   Dashboard: http://localhost:8080                           ║");
        log.info("║   H2 Console: http://localhost:8080/h2-console               ║");
        log.info("╚══════════════════════════════════════════════════════════════╝");
    }
}
