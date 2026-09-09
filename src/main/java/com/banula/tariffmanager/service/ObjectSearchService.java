package com.banula.tariffmanager.service;

import com.banula.tariffmanager.config.MongoCollectionMapper;
import com.banula.openlib.mongodb.util.GenericMongoMapper;
import com.banula.tariffmanager.model.OnChainTariff;
import com.banula.openlib.ocpi.model.dto.TariffDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ObjectSearchService {
    private final MongoTemplate mongo;
    private final MongoCollectionMapper collections;
    private final GenericMongoMapper mapper;

    public record SearchPage(List<?> items, long total, int offset, int limit) {}

    public SearchPage search(String module, String tenant, String id, String name,
            String countryCode, String partyId, LocalDateTime dateFrom, LocalDateTime dateTo, int offset, int limit) {
        if (offset < 0 || limit < 1 || limit > 200) throw bad("offset must be nonnegative and limit must be 1–200");
        if (!Set.of("tariffs").contains(module)) throw bad("Unsupported object module");
        List<Criteria> filters = new ArrayList<>();

        if (text(id)) filters.add(Criteria.where(module.equals("tokens") ? "uid" : "id").regex(literal(id)));
        if (text(name)) {
            if (!module.equals("locations")) throw bad("Name filtering is supported only for locations");
            filters.add(Criteria.where("name").regex(literal(name)));
        }
        if (text(countryCode)) {
            countryCode = countryCode.trim().toUpperCase(Locale.ROOT);
            if (!countryCode.matches("[A-Z]{2}")) throw bad("Owner country code must have two letters");
            filters.add(Criteria.where("countryCode").is(countryCode));
        }
        if (text(partyId)) {
            partyId = partyId.trim().toUpperCase(Locale.ROOT);
            if (!partyId.matches("[A-Z0-9]{3}")) throw bad("Owner party ID must have three characters");
            filters.add(Criteria.where("partyId").is(partyId));
        }
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) throw bad("date_from must precede date_to");
        if (dateFrom != null || dateTo != null) {
            Criteria date = Criteria.where("lastUpdated");
            if (dateFrom != null) date.gte(dateFrom);
            if (dateTo != null) date.lte(dateTo);
            filters.add(date);
        }

        Query query = filters.isEmpty() ? new Query() : Query.query(new Criteria().andOperator(filters.toArray(Criteria[]::new)));
        return switch (module) {
            case "tariffs" -> read(query, OnChainTariff.class, TariffDTO.class, collections.getTariffCollectionName(), offset, limit);
            default -> throw bad("Unsupported object module");
        };
    }

    private <M,D> SearchPage read(Query query, Class<M> entity, Class<D> dto, String collection, int offset, int limit) {
        long total = mongo.count(query, entity, collection);
        query.with(Sort.by(Sort.Order.desc("lastUpdated"), Sort.Order.asc("_id"))).skip(offset).limit(limit);
        List<D> items = mongo.find(query, entity, collection).stream().map(row -> mapper.toDTO(row, dto)).toList();
        return new SearchPage(items, total, offset, limit);
    }
    private boolean text(String value) { return value != null && !value.isBlank(); }
    private Pattern literal(String value) {
        if (value.length() > 256) throw bad("Search text must be at most 256 characters");
        return Pattern.compile(Pattern.quote(value.trim()), Pattern.CASE_INSENSITIVE);
    }
    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
