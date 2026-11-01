package com.motorbike.fuel;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Import giornaliero (app.fuel.import-cron) e, al primo avvio, import immediato se la tabella è vuota. */
@Component
@ConditionalOnProperty(name = "app.fuel.import-enabled", havingValue = "true")
class FuelImportScheduler {

    private final FuelImportService service;

    FuelImportScheduler(FuelImportService service) {
        this.service = service;
    }

    @Scheduled(cron = "${app.fuel.import-cron}", zone = "Europe/Rome")
    void daily() {
        service.importAll();
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    void onStartup() {
        if (service.stationCount() == 0) {
            service.importAll();
        }
    }
}
