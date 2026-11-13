package com.som.account.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(prefix = "som.engine", name = "mode", havingValue = "real")
public class EngineDeletionRetry {

    private final DeletionService deletions;

    public EngineDeletionRetry(DeletionService deletions) {
        this.deletions = deletions;
    }

    @Scheduled(fixedDelayString = "${som.engine.deletion-retry-delay:30s}",
               initialDelayString = "${som.engine.deletion-retry-delay:30s}")
    void retry() {
        deletions.retryPending();
    }
}
