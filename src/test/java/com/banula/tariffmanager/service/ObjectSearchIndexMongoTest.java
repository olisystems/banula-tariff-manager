package com.banula.tariffmanager.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.banula.openlib.mongodb.util.GenericMongoMapper;
import com.banula.tariffmanager.config.MongoCollectionMapper;
import com.banula.tariffmanager.config.ObjectSearchIndexConfig;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;

import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.*;

@EnabledIfEnvironmentVariable(named = "OBJECT_SEARCH_MONGO_URI", matches = ".+")
class ObjectSearchIndexMongoTest {
    private static final String SEARCH_COLLECTION = "collection0";
    private static final Instant RANGE_BOUNDARY = Instant.parse("2026-09-01T12:00:00Z");
    private MongoClient client;
    private MongoTemplate mongo;
    private MongoCollectionMapper collections;
    private TimeZone originalZone;

    @BeforeEach
    void createIsolatedDatabase() {
        originalZone = TimeZone.getDefault();
        client = MongoClients.create(System.getenv("OBJECT_SEARCH_MONGO_URI"));
        mongo =
                new MongoTemplate(
                        client, "object_search_" + UUID.randomUUID().toString().replace("-", ""));
        collections = mock(MongoCollectionMapper.class);
        when(collections.getTariffCollectionName()).thenReturn("collection0");
    }

    @AfterEach
    void cleanUp() {
        try {
            if (mongo != null) mongo.getDb().drop();
        } finally {
            try {
                if (client != null) client.close();
            } finally {
                TimeZone.setDefault(originalZone);
            }
        }
    }

    @Test
    void createsIndexesRepeatablyOnEverySearchCollection() {
        var indexes = new ObjectSearchIndexConfig(mongo, collections);
        indexes.afterPropertiesSet();
        indexes.afterPropertiesSet();
        for (String collection : mongo.getCollectionNames()) {
            var names =
                    mongo.getCollection(collection)
                            .listIndexes()
                            .map(index -> index.getString("name"))
                            .into(new ArrayList<>());
            assertTrue(
                    names.containsAll(
                            List.of(
                                    "object_search_recent",
                                    "object_search_owner_recent",
                                    "object_search_party_recent")),
                    collection);
        }
    }

    @Test
    void preservesUtcDateBoundsOnNonUtcHosts() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Guatemala"));
        insertRowsAroundBoundary();
        var service = new ObjectSearchService(mongo, collections, new GenericMongoMapper());
        var criteria =
                SearchCriteria.builder()
                        .countryCode("DE")
                        .partyId("OLI")
                        .dateFrom(RANGE_BOUNDARY)
                        .dateTo(RANGE_BOUNDARY)
                        .offset(100)
                        .build();
        // Count verifies BSON date conversion; skip DTO mapping for these minimal raw documents.
        assertEquals(1, service.search("tariffs", criteria).total());
    }

    @Test
    void scopedSubstringSearchUsesIndexWithoutBlockingSort() {
        new ObjectSearchIndexConfig(mongo, collections).afterPropertiesSet();
        insertRowsAroundBoundary();
        var filter =
                ownerFilter()
                        .append("id", new Document("$regex", "location").append("$options", "i"));
        var find =
                new Document("find", SEARCH_COLLECTION)
                        .append("filter", filter)
                        .append("sort", new Document("lastUpdated", -1).append("_id", 1));
        var planner =
                mongo.executeCommand(new Document("explain", find))
                        .get("queryPlanner", Document.class);
        var winningPlan = planner.get("winningPlan", Document.class);
        assertTrue(hasStage(winningPlan, "IXSCAN"), winningPlan::toJson);
        assertFalse(hasStage(winningPlan, "SORT"), winningPlan::toJson);
    }

    private void insertRowsAroundBoundary() {
        for (int hourOffset = -1; hourOffset <= 1; hourOffset++) {
            var row =
                    ownerFilter()
                            .append("_id", "row" + hourOffset)
                            .append("id", "location-" + hourOffset)
                            .append(
                                    "lastUpdated",
                                    Date.from(RANGE_BOUNDARY.plusSeconds(hourOffset * 3600)));
            mongo.getCollection(SEARCH_COLLECTION).insertOne(row);
        }
    }

    private Document ownerFilter() {
        return new Document("countryCode", "DE").append("partyId", "OLI");
    }

    private boolean hasStage(Object node, String stage) {
        if (node instanceof Map<?, ?> document) {
            return stage.equals(document.get("stage"))
                    || document.values().stream().anyMatch(value -> hasStage(value, stage));
        }
        if (node instanceof List<?> children) {
            return children.stream().anyMatch(value -> hasStage(value, stage));
        }
        return false;
    }
}
