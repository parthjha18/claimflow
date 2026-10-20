package com.claimflow.claim.report;

import com.claimflow.claim.domain.ClaimStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "Reports")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN','FINANCE')")
public class ReportController {

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @GetMapping("/spend")
    @Operation(summary = "Total spend by month and/or category for my organisation (paginated, sortable)",
            description = "status defaults to PAID. sort: month|category|total|itemCount|claimCount. "
                    + "Dates filter on the expense date of each line item.")
    public SpendReportResponse spend(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "MONTH_CATEGORY") ReportService.GroupBy groupBy,
            @RequestParam(required = false) List<ClaimStatus> status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "desc") String dir) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusMonths(12);
        List<ClaimStatus> statuses = status == null || status.isEmpty() ? List.of(ClaimStatus.PAID) : status;
        return service.spend(start, end, groupBy, statuses, page, size, sort, !"asc".equalsIgnoreCase(dir));
    }
}
