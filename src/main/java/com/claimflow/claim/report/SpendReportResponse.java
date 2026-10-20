package com.claimflow.claim.report;

import com.claimflow.common.web.PageResponse;

import java.math.BigDecimal;

public record SpendReportResponse(BigDecimal grandTotal, String currency, PageResponse<SpendRow> rows) {
}
