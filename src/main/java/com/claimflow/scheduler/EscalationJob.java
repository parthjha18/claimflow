package com.claimflow.scheduler;

import com.claimflow.claim.ClaimRepository;
import com.claimflow.claim.ClaimWorkflowService;
import com.claimflow.policy.ExpensePolicyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Hands claims that have waited longer than their organisation's {@code escalationAfterDays} to the next
 * manager up the chain. Each claim is escalated in its own transaction, so one bad row cannot block the rest.
 *
 * Safe to run on several instances at once: escalation bumps the claim's {@code @Version}, so if two
 * instances (or a manager clicking "approve") touch the same claim, exactly one write wins and the others
 * are skipped. No distributed lock is needed.
 */
@Component
public class EscalationJob {

    private static final Logger log = LoggerFactory.getLogger(EscalationJob.class);

    private final ExpensePolicyRepository policies;
    private final ClaimRepository claims;
    private final ClaimWorkflowService workflow;
    private final Clock clock;

    public EscalationJob(ExpensePolicyRepository policies, ClaimRepository claims, ClaimWorkflowService workflow,
                         Clock clock) {
        this.policies = policies;
        this.claims = claims;
        this.workflow = workflow;
        this.clock = clock;
    }

    @Scheduled(cron = "${claimflow.escalation.cron:0 0 * * * *}")   // hourly by default
    public void scheduledRun() {
        int escalated = runOnce();
        if (escalated > 0) {
            log.info("Escalation run finished: {} claim(s) escalated", escalated);
        }
    }

    /** One pass over every organisation; returns how many claims were escalated. */
    public int runOnce() {
        Instant now = clock.instant();
        int escalated = 0;
        for (var setting : policies.findEscalationSettings()) {
            Instant cutoff = now.minus(Duration.ofDays(setting.getDays()));
            for (UUID claimId : claims.findOverdueSubmitted(setting.getOrganizationId(), cutoff)) {
                try {
                    if (workflow.escalate(claimId, cutoff, setting.getDays())) {
                        escalated++;
                    }
                } catch (OptimisticLockingFailureException e) {
                    log.debug("Claim {} changed while escalating; skipping", claimId);
                } catch (RuntimeException e) {
                    log.error("Could not escalate claim {}", claimId, e);
                }
            }
        }
        return escalated;
    }
}
