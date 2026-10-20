package com.claimflow.policy;

import com.claimflow.claim.domain.Claim;
import com.claimflow.claim.domain.ClaimLineItem;
import com.claimflow.claim.domain.ExpenseCategory;
import com.claimflow.common.exception.PolicyViolationException;
import com.claimflow.user.Role;
import com.claimflow.user.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static com.claimflow.claim.domain.ExpenseCategory.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PolicyValidatorTest {

    private final PolicyValidator validator = new PolicyValidator();
    private final UUID org = UUID.randomUUID();

    /** per-claim max 50,000; receipts above 1,000; finance above 10,000; TRAVEL<=20,000 FOOD<=5,000 LODGING<=15,000 */
    private final PolicySnapshot policy = new PolicySnapshot(org, bd(50000), bd(1000), bd(10000), 3,
            Map.of(TRAVEL, bd(20000), FOOD, bd(5000), LODGING, bd(15000)));

    private Claim claim() {
        User u = new User(org, "e@x.com", "hash", "Emp", Role.EMPLOYEE, null);
        return new Claim(u, "Trip", null);
    }

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v).setScale(2);
    }

    private static ClaimLineItem add(Claim c, ExpenseCategory cat, double amount, String desc, boolean receipt) {
        ClaimLineItem item = c.addItem(cat, bd(amount), LocalDate.now(), desc);
        if (receipt) {
            item.attachReceipt("ref", "r.pdf", "application/pdf");
        }
        return item;
    }

    @Test
    void validClaimHasNoViolations() {
        Claim c = claim();
        add(c, FOOD, 800, "Lunch", false);
        add(c, TRAVEL, 4000, "Flight", true);
        assertThat(validator.findViolations(policy, c)).isEmpty();
    }

    @Test
    void emptyClaimIsRejected() {
        assertThat(validator.findViolations(policy, claim())).containsExactly("A claim needs at least one line item");
    }

    @Test
    void receiptRequiredAboveThresholdButNotAtThreshold() {
        Claim c = claim();
        add(c, FOOD, 1000, "Exactly at threshold", false);
        assertThat(validator.findViolations(policy, c)).isEmpty();

        add(c, FOOD, 1000.01, "Dinner", false);
        assertThat(validator.findViolations(policy, c)).singleElement().asString().contains("Dinner", "needs a receipt");
    }

    @Test
    void categoryLimitAppliesToTheSumOfItemsInThatCategory() {
        Claim c = claim();
        add(c, FOOD, 900, "Day 1", false);
        add(c, FOOD, 900, "Day 2", false);
        add(c, FOOD, 900, "Day 3", false);
        add(c, FOOD, 900, "Day 4", false);
        add(c, FOOD, 900, "Day 5", false);
        add(c, FOOD, 900, "Day 6", false);
        assertThat(validator.findViolations(policy, c)).singleElement().asString().contains("FOOD total 5400.00", "5000.00");
    }

    @Test
    void categoryWithoutALimitIsUnrestricted() {
        Claim c = claim();
        add(c, OTHER, 9000, "Misc", true);
        assertThat(validator.findViolations(policy, c)).isEmpty();
    }

    @Test
    void perClaimMaximumIsEnforced() {
        Claim c = claim();
        add(c, OTHER, 30000, "Equipment", true);
        add(c, OTHER, 30000, "More equipment", true);
        assertThat(validator.findViolations(policy, c)).singleElement().asString().contains("per-claim maximum");
    }

    @Test
    void allViolationsAreReportedTogether() {
        Claim c = claim();
        add(c, FOOD, 6000, "Banquet", false);
        assertThat(validator.findViolations(policy, c)).hasSize(2);
    }

    @Test
    void validateOrThrowRaisesUnprocessableEntityWithViolations() {
        Claim c = claim();
        add(c, FOOD, 6000, "Banquet", true);
        assertThatThrownBy(() -> validator.validateOrThrow(policy, c))
                .isInstanceOfSatisfying(PolicyViolationException.class, e -> {
                    assertThat(e.getStatus().value()).isEqualTo(422);
                    assertThat(e.getViolations()).hasSize(1);
                });
    }

    @Test
    void financeApprovalThresholdIsExclusive() {
        assertThat(policy.requiresFinance(bd(10000))).isFalse();
        assertThat(policy.requiresFinance(bd(10000.01))).isTrue();
    }
}
