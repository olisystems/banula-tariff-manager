package com.banula.tariffmanager.service;

import com.banula.openlib.mongodb.util.GenericMongoMapper;
import com.banula.openlib.ocpi.model.dto.TariffDTO;
import com.banula.tariffmanager.config.MongoCollectionMapper;
import com.banula.tariffmanager.model.OnChainTariff;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ObjectSearchService {
    private static final Duration QUERY_TIMEOUT = Duration.ofSeconds(10);
    private static final Set<String> SUPPORTED_MODULES = Set.of("tariffs");
    private final MongoTemplate mongo;
    private final MongoCollectionMapper collections;
    private final GenericMongoMapper mapper;

    public record SearchPage(List<?> items, long total, int offset, int limit) {}

    public SearchPage search(String module, SearchCriteria criteria) {
        validateSearch(module, criteria);
        List<Criteria> filters = new ArrayList<>();

        addTextFilters(filters, criteria);
        addOwnerFilters(filters, criteria);
        addDateFilters(filters, criteria);

        Query query =
                filters.isEmpty()
                        ? new Query()
                        : Query.query(new Criteria().andOperator(filters.toArray(Criteria[]::new)));
        return read(
                query,
                OnChainTariff.class,
                TariffDTO.class,
                collections.getTariffCollectionName(),
                criteria.offset(),
                criteria.limit());
    }

    private void validateSearch(String module, SearchCriteria criteria) {
        if (criteria.offset() < 0
                || criteria.limit() < 1
                || criteria.limit() > SearchCriteria.MAX_LIMIT) {
            throw bad("offset must be nonnegative and limit must be 1–200");
        }
        if (!SUPPORTED_MODULES.contains(module)) {
            throw bad("Unsupported object module");
        }
        if (criteria.dateFrom() != null
                && criteria.dateTo() != null
                && criteria.dateFrom().isAfter(criteria.dateTo())) {
            throw bad("date_from must precede date_to");
        }
    }

    private void addTextFilters(List<Criteria> filters, SearchCriteria criteria) {
        if (text(criteria.id())) {
            filters.add(Criteria.where("id").regex(literal(criteria.id())));
        }
        if (text(criteria.name())) {
            throw bad("Name filtering is supported only for locations");
        }
    }

    private void addOwnerFilters(List<Criteria> filters, SearchCriteria criteria) {
        if (text(criteria.countryCode())) {
            String country = criteria.countryCode().trim().toUpperCase(Locale.ROOT);
            if (!country.matches("[A-Z]{2}")) {
                throw bad("Owner country code must have two letters");
            }
            filters.add(Criteria.where("countryCode").is(country));
        }
        if (text(criteria.partyId())) {
            String party = criteria.partyId().trim().toUpperCase(Locale.ROOT);
            if (!party.matches("[A-Z0-9]{3}")) {
                throw bad("Owner party ID must have three characters");
            }
            filters.add(Criteria.where("partyId").is(party));
        }
    }

    private void addDateFilters(List<Criteria> filters, SearchCriteria criteria) {
        if (criteria.dateFrom() != null || criteria.dateTo() != null) {
            Criteria date = Criteria.where("lastUpdated");
            if (criteria.dateFrom() != null) date.gte(Date.from(criteria.dateFrom()));
            if (criteria.dateTo() != null) date.lte(Date.from(criteria.dateTo()));
            filters.add(date);
        }
    }

    private <M, D> SearchPage read(
            Query query, Class<M> entity, Class<D> dto, String collection, int offset, int limit) {
        // Substring matching intentionally remains literal and case-insensitive.
        // Scope/sort indexes reduce work; bound the residual scan on large datasets.
        query.maxTime(QUERY_TIMEOUT);
        long total = mongo.count(query, entity, collection);
        query.allowDiskUse(true)
                .with(Sort.by(Sort.Order.desc("lastUpdated"), Sort.Order.asc("_id")))
                .skip(offset)
                .limit(limit);
        List<D> items =
                mongo.find(query, entity, collection).stream()
                        .map(row -> mapper.toDTO(row, dto))
                        .toList();
        return new SearchPage(items, total, offset, limit);
    }

    private boolean text(String value) {
        return value != null && !value.isBlank();
    }

    private Pattern literal(String value) {
        if (value.length() > SearchCriteria.MAX_TEXT_LENGTH)
            throw bad("Search text must be at most 256 characters");
        return Pattern.compile(Pattern.quote(value.trim()), Pattern.CASE_INSENSITIVE);
    }

    private ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
