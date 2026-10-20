package com.claimflow.claim.report;

import java.math.BigDecimal;

/** One row of the spend report; {@code month} (YYYY-MM) or {@code category} is null when not grouped by it. */
public record SpendRow(String month, String category, BigDecimal total, long itemCount, long claimCount) {
}
