package com.banula.tariffmanager.service;
import com.banula.tariffmanager.config.MongoCollectionMapper;
import com.banula.tariffmanager.config.ObjectSearchIndexConfig;
import com.banula.openlib.mongodb.util.GenericMongoMapper;
import com.mongodb.client.MongoClients;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.time.Instant;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="OBJECT_SEARCH_MONGO_URI", matches=".+")
class ObjectSearchIndexMongoTest {
 @Test void indexesAreRepeatableAndUtcBoundsDoNotDependOnJvmTimezone() {
  var zone=TimeZone.getDefault();
  try(var client=MongoClients.create(System.getenv("OBJECT_SEARCH_MONGO_URI"))) {
   var mongo=new MongoTemplate(client,"object_search_"+UUID.randomUUID().toString().replace("-",""));
   try {
    TimeZone.setDefault(TimeZone.getTimeZone("America/Guatemala"));
    var collections=mock(MongoCollectionMapper.class);
    when(collections.getTariffCollectionName()).thenReturn("collection0");
    var indexes=new ObjectSearchIndexConfig(mongo,collections);
    indexes.afterPropertiesSet();
    indexes.afterPropertiesSet();
    for(String collection:mongo.getCollectionNames()) {
     var names=mongo.getCollection(collection).listIndexes().map(d->d.getString("name")).into(new ArrayList<>());
     assertTrue(names.containsAll(List.of("object_search_recent","object_search_owner_recent","object_search_party_recent")));
    }
    var from=Instant.parse("2026-09-01T12:00:00Z");
    for(int i=-1;i<=1;i++) mongo.getCollection("collection0").insertOne(new Document("_id","row"+i)
      .append("id","location-"+i).append("countryCode","DE").append("partyId","OLI")
      .append("lastUpdated",Date.from(from.plusSeconds(i*3600))));
    var service=new ObjectSearchService(mongo,collections,new GenericMongoMapper());
    // Skip DTO mapping: this assertion targets BSON date conversion and count semantics.
    assertEquals(1,service.search("tariffs",null,null,"DE","OLI",from,from,100,25).total());
    var filter=new Document("countryCode","DE").append("partyId","OLI");
    filter.append("id",new Document("$regex","location").append("$options","i"));
    var explain=mongo.executeCommand(new Document("explain",new Document("find","collection0")
      .append("filter",filter).append("sort",new Document("lastUpdated",-1).append("_id",1)))).get("queryPlanner",Document.class);
    String plan=explain.get("winningPlan",Document.class).toJson();
    assertTrue(plan.contains("IXSCAN"),plan);
    assertFalse(plan.contains("\"stage\": \"SORT\""),plan);
   } finally { mongo.getDb().drop(); TimeZone.setDefault(zone); }
  }
 }
}
