package com.claimflow.notification;

import com.claimflow.claim.domain.ClaimStatus;
import com.claimflow.user.Role;
import com.claimflow.user.User;
import com.claimflow.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns status changes into emails. Runs on the notification executor after the transition committed,
 * so a slow or failing mail server can neither slow down nor fail an approval.
 */
@Component
public class ClaimNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(ClaimNotificationListener.class);

    private final UserRepository users;
    private final EmailSender mail;

    public ClaimNotificationListener(UserRepository users, EmailSender mail) {
        this.users = users;
        this.mail = mail;
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(readOnly = true, propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void onStatusChanged(ClaimStatusChangedEvent e) {
        try {
            Set<String> recipients = new LinkedHashSet<>();
            users.findById(e.claimantId()).ifPresent(u -> recipients.add(u.getEmail()));

            if (e.toStatus() == ClaimStatus.SUBMITTED && e.currentApproverId() != null) {
                users.findById(e.currentApproverId()).ifPresent(u -> recipients.add(u.getEmail()));
            }
            if (needsFinanceAttention(e)) {
                List<User> finance = users.findByOrganizationIdAndRoleAndActiveTrue(e.organizationId(), Role.FINANCE);
                finance.forEach(u -> recipients.add(u.getEmail()));
            }

            String subject = "Claim \"" + e.claimTitle() + "\" is now " + e.toStatus();
            String body = "Action " + e.action() + " moved the claim from " + e.fromStatus() + " to " + e.toStatus()
                    + " (amount " + e.totalAmount() + " INR)."
                    + (e.comment() != null && !e.comment().isBlank() ? " Comment: " + e.comment() : "");
            recipients.forEach(to -> mail.send(to, subject, body));
        } catch (RuntimeException ex) {
            // notifications are best-effort; never propagate into the (already committed) business flow
            log.error("Failed to send notifications for claim {}", e.claimId(), ex);
        }
    }

    private static boolean needsFinanceAttention(ClaimStatusChangedEvent e) {
        return e.toStatus() == ClaimStatus.MANAGER_APPROVED || e.toStatus() == ClaimStatus.FINANCE_APPROVED;
    }
}
