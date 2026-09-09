package com.banula.tariffmanager.config;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

import java.util.List;

/** Index equality scopes and deterministic ordering without changing substring semantics. */
@Component
@RequiredArgsConstructor
public class ObjectSearchIndexConfig implements InitializingBean {
    private final MongoTemplate mongo;
    private final MongoCollectionMapper collections;

    @Override
    public void afterPropertiesSet() {
        for (String collection : List.of(collections.getTariffCollectionName())) {
            var indexes = mongo.indexOps(collection);
            indexes.ensureIndex(recent(scope()).named("object_search_recent"));
            indexes.ensureIndex(
                    recent(
                                    scope().on("countryCode", Sort.Direction.ASC)
                                            .on("partyId", Sort.Direction.ASC))
                            .named("object_search_owner_recent"));
            indexes.ensureIndex(
                    recent(scope().on("partyId", Sort.Direction.ASC))
                            .named("object_search_party_recent"));
        }
    }

    private Index scope() {
        return new Index();
    }

    private Index recent(Index index) {
        return index.on("lastUpdated", Sort.Direction.DESC).on("_id", Sort.Direction.ASC);
    }
}
