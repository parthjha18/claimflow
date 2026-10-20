package com.claimflow.scheduler;

import com.claimflow.idempotency.IdempotencyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

@Component
public class MaintenanceJobs {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceJobs.class);
    static final Duration IDEMPOTENCY_RETENTION = Duration.ofHours(24);

    private final IdempotencyService idempotency;
    private final Clock clock;

    public MaintenanceJobs(IdempotencyService idempotency, Clock clock) {
        this.idempotency = idempotency;
        this.clock = clock;
    }

    /** Keys only need to outlive a client's realistic retry window. */
    @Scheduled(cron = "${claimflow.maintenance.cron:0 30 * * * *}")
    public void purgeIdempotencyKeys() {
        int removed = idempotency.purgeOlderThan(clock.instant().minus(IDEMPOTENCY_RETENTION));
        if (removed > 0) {
            log.info("Purged {} expired idempotency keys", removed);
        }
    }
}
