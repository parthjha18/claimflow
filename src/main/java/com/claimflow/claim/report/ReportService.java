package com.claimflow.claim.report;

import com.claimflow.claim.domain.ClaimStatus;
import com.claimflow.common.exception.BadRequestException;
import com.claimflow.common.tenant.TenantContext;
import com.claimflow.common.web.PageResponse;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Spend aggregation done in SQL (GROUP BY on the indexed organisation/date/category columns) rather than by
 * loading claims into memory. ORDER BY comes from a fixed whitelist, never from client text.
 */
@Service
@Transactional(readOnly = true)
public class ReportService {

    public enum GroupBy { MONTH, CATEGORY, MONTH_CATEGORY }

    private static final int MAX_SIZE = 100;
    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "month", "month", "category", "category", "total", "total",
            "itemCount", "item_count", "claimCount", "claim_count");

    private final NamedParameterJdbcTemplate jdbc;

    public ReportService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public SpendReportResponse spend(LocalDate from, LocalDate to, GroupBy groupBy, List<ClaimStatus> statuses,
                                     int page, int size, String sort, boolean descending) {
        if (from.isAfter(to)) {
            throw new BadRequestException("INVALID_RANGE", "'from' must not be after 'to'");
        }
        if (page < 0 || size < 1) {
            throw new BadRequestException("INVALID_PAGE", "page must be >= 0 and size >= 1");
        }
        size = Math.min(size, MAX_SIZE);

        boolean byMonth = groupBy != GroupBy.CATEGORY;
        boolean byCategory = groupBy != GroupBy.MONTH;
        String sortColumn = SORT_COLUMNS.get(sort == null ? defaultSort(groupBy) : sort);
        if (sortColumn == null || (sortColumn.equals("month") && !byMonth) || (sortColumn.equals("category") && !byCategory)) {
            throw new BadRequestException("INVALID_SORT", "Cannot sort by '" + sort + "' when grouping by " + groupBy);
        }

        String monthExpr = "to_char(date_trunc('month', i.expense_date), 'YYYY-MM')";
        String select = (byMonth ? monthExpr + " as month, " : "null as month, ")
                + (byCategory ? "i.category as category, " : "null as category, ")
                + "sum(i.amount) as total, count(*) as item_count, count(distinct i.claim_id) as claim_count";
        String groupCols = (byMonth ? monthExpr : "") + (byMonth && byCategory ? ", " : "") + (byCategory ? "i.category" : "");
        String from_ = """
                from claim_line_items i join claims c on c.id = i.claim_id
                where i.organization_id = :org and c.status in (:statuses) and i.expense_date between :from and :to
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("org", TenantContext.organizationId())
                .addValue("statuses", statuses.stream().map(Enum::name).toList())
                .addValue("from", from).addValue("to", to)
                .addValue("limit", size).addValue("offset", (long) page * size);

        String order = sortColumn + (descending ? " desc" : " asc") + ", " + groupCols.replace("i.category", "category");
        List<SpendRow> rows = jdbc.query(
                "select " + select + " " + from_ + " group by " + groupCols + " order by " + order + " limit :limit offset :offset",
                params,
                (rs, i) -> new SpendRow(rs.getString("month"), rs.getString("category"), rs.getBigDecimal("total"),
                        rs.getLong("item_count"), rs.getLong("claim_count")));

        Long groups = jdbc.queryForObject("select count(*) from (select 1 " + from_ + " group by " + groupCols + ") g",
                params, Long.class);
        BigDecimal grand = jdbc.queryForObject("select coalesce(sum(i.amount), 0) " + from_, params, BigDecimal.class);

        var pageable = PageRequest.of(page, size);
        return new SpendReportResponse(grand, "INR", PageResponse.of(new PageImpl<>(rows, pageable, groups == null ? 0 : groups)));
    }

    private static String defaultSort(GroupBy g) {
        return g == GroupBy.CATEGORY ? "category" : "month";
    }
}
