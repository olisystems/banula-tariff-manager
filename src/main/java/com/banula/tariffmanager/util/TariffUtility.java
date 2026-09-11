package com.banula.tariffmanager.util;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.data.domain.Sort;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import com.banula.tariffmanager.config.MongoCollectionMapper;
import com.banula.tariffmanager.model.OnChainTariff;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@AllArgsConstructor
@Slf4j
public class TariffUtility {

    private final MongoTemplate mongoTemplate;
    private final MongoCollectionMapper mongoCollectionMapper;

    public List<OnChainTariff> findTariffs(LocalDateTime dateFrom, LocalDateTime dateTo, Integer offset, Integer limit) {
        return findTariffs(dateFrom, dateTo, offset, limit, null, null, null);
    }

    public List<OnChainTariff> findTariffs(LocalDateTime dateFrom, LocalDateTime dateTo, Integer offset, Integer limit,
            String countryCode, String partyId, String tariffId) {
        Query query = createQueryForCdrFetching(dateFrom, dateTo);
        if (countryCode != null && !countryCode.isBlank()) query.addCriteria(Criteria.where("countryCode").is(countryCode.trim().toUpperCase(Locale.ROOT)));
        if (partyId != null && !partyId.isBlank()) query.addCriteria(Criteria.where("partyId").is(partyId.trim().toUpperCase(Locale.ROOT)));
        if (tariffId != null && !tariffId.isBlank()) query.addCriteria(Criteria.where("id").regex(Pattern.quote(tariffId.trim()), "i"));
        query.with(Sort.by("countryCode", "partyId", "id", "hashTag"));
        query.skip(offset != null ? offset : 0);
        query.limit(limit != null ? limit : Integer.MAX_VALUE);
        return mongoTemplate.find(query, OnChainTariff.class, mongoCollectionMapper.getTariffCollectionName());
    }

    private Query createQueryForCdrFetching(LocalDateTime dateFrom, LocalDateTime dateTo) {
        Query query = new Query();
        if (dateFrom != null)
            query.addCriteria(Criteria.where("startDateTime").gte(dateFrom));
        if (dateTo != null)
            query.addCriteria(Criteria.where("endDateTime").lte(dateTo));
        return query;
    }

}
