package com.banula.tariffmanager.service;

import lombok.Builder;

import java.time.Instant;

/** Optional object filters and pagination, independent from the selected tenant. */
@Builder
public record SearchCriteria(
        String id,
        String name,
        String countryCode,
        String partyId,
        Instant dateFrom,
        Instant dateTo,
        int offset,
        int limit) {
    public static final int DEFAULT_LIMIT = 25;
    public static final int MAX_LIMIT = 200;
    public static final int MAX_TEXT_LENGTH = 256;

    public static class SearchCriteriaBuilder {
        private int limit = DEFAULT_LIMIT;
    }
}
