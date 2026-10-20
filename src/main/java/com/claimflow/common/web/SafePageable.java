package com.claimflow.common.web;

import com.claimflow.common.exception.BadRequestException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

/** Clamps page size and only allows sorting on properties we expose (and have indexes or small result sets for). */
public final class SafePageable {

    public static final int MAX_PAGE_SIZE = 100;

    private SafePageable() {
    }

    public static Pageable of(Pageable requested, Set<String> sortableProperties, Sort defaultSort) {
        for (Sort.Order order : requested.getSort()) {
            if (!sortableProperties.contains(order.getProperty())) {
                throw new BadRequestException("INVALID_SORT",
                        "Cannot sort by '" + order.getProperty() + "'; allowed: " + sortableProperties);
            }
        }
        Sort sort = requested.getSort().isSorted() ? requested.getSort() : defaultSort;
        // a stable tie-breaker keeps pages consistent when many rows share the sort value
        sort = sort.and(Sort.by(Sort.Direction.ASC, "id"));
        return PageRequest.of(requested.getPageNumber(), Math.min(requested.getPageSize(), MAX_PAGE_SIZE), sort);
    }
}
