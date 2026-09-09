package com.banula.tariffmanager.service;
import com.banula.tariffmanager.config.MongoCollectionMapper;
import com.banula.openlib.mongodb.util.GenericMongoMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.util.regex.Pattern;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class ObjectSearchServiceTest {
 @Test void combinesLiteralFiltersBeforeCountingAndPaging() {
  MongoTemplate mongo = mock(MongoTemplate.class);
  MongoCollectionMapper collections = mock(MongoCollectionMapper.class);
  when(collections.getTariffCollectionName()).thenReturn("objects");
  List<Query> captured = new ArrayList<>();
  when(mongo.count(any(Query.class), any(Class.class), eq("objects"))).thenAnswer(call -> {
   Query query = call.getArgument(0);
   assertEquals(0, query.getSkip()); assertEquals(0, query.getLimit());
   captured.add(query); return 12L;
  });
  when(mongo.find(any(Query.class), any(Class.class), eq("objects"))).thenAnswer(call -> {
   Query query = call.getArgument(0);
   assertEquals(10, query.getSkip()); assertEquals(10, query.getLimit());
   assertEquals(-1, query.getSortObject().get("lastUpdated"));
   assertEquals(1, query.getSortObject().get("_id"));
   return List.of();
  });
  ObjectSearchService service = new ObjectSearchService(mongo, collections, mock(GenericMongoMapper.class));
  var result = service.search("tariffs", "a.*", null, "de", "oli", null, null, 10, 10);
  assertEquals(12, result.total()); assertEquals(10, result.offset());
  var terms = (List<org.bson.Document>) captured.get(0).getQueryObject().get("$and");
  assertTrue(terms.stream().anyMatch(term -> "DE".equals(term.get("countryCode"))));
  assertTrue(terms.stream().anyMatch(term -> "OLI".equals(term.get("partyId"))));
  Pattern id = (Pattern) terms.stream().filter(term -> term.containsKey("id")).findFirst().orElseThrow().get("id");
  assertTrue(id.matcher("testA.*suffix").find()); assertFalse(id.matcher("abc").find());

  verify(mongo).count(any(Query.class), any(Class.class), eq("objects"));
 }
 @Test void rejectsInvalidFiltersWithoutQueryingMongo() {
  MongoTemplate mongo = mock(MongoTemplate.class);
  ObjectSearchService service = new ObjectSearchService(mongo, mock(MongoCollectionMapper.class), mock(GenericMongoMapper.class));
  assertThrows(ResponseStatusException.class, () -> service.search("tariffs", null,null,null,null,null,null,-1,10));
  assertThrows(ResponseStatusException.class, () -> service.search("tariffs", null,null,null,null,null,null,0,201));
  assertThrows(ResponseStatusException.class, () -> service.search("tariffs", null,null,"Germany",null,null,null,0,10));
  assertThrows(ResponseStatusException.class, () -> service.search("unknown", null,null,null,null,null,null,0,10));
  verifyNoInteractions(mongo);
 }

 @Test void keepsUtcBoundsAndModuleFieldSelectionIndependentOfHostTimezone() {
  TimeZone previous = TimeZone.getDefault();
  try {
   TimeZone.setDefault(TimeZone.getTimeZone("America/Guatemala"));
   var from = java.time.Instant.parse("2026-09-09T10:00:00Z");
   var to = java.time.Instant.parse("2026-09-10T10:00:00Z");
   for (String module : List.of("tariffs")) {
    MongoTemplate mongo = mock(MongoTemplate.class);
    MongoCollectionMapper collections = mock(MongoCollectionMapper.class);
    when(collections.getTariffCollectionName()).thenReturn("objects");
    when(mongo.find(any(Query.class), any(Class.class), eq("objects"))).thenReturn(List.of());
    ObjectSearchService service = new ObjectSearchService(mongo, collections, mock(GenericMongoMapper.class));
    service.search(module, "needle", null, null, null, from, to, 0, 10);
    var query = org.mockito.ArgumentCaptor.forClass(Query.class);
    verify(mongo).find(query.capture(), any(Class.class), eq("objects"));
    var terms = (List<org.bson.Document>) query.getValue().getQueryObject().get("$and");
    assertTrue(terms.stream().anyMatch(term -> term.containsKey(module.equals("tokens") ? "uid" : "id")));
    String fromField = "lastUpdated";
    String toField = "lastUpdated";
    var fromTerm = (org.bson.Document) terms.stream().filter(term -> term.containsKey(fromField)).findFirst().orElseThrow().get(fromField);
    var toTerm = (org.bson.Document) terms.stream().filter(term -> term.containsKey(toField)).findFirst().orElseThrow().get(toField);
    assertEquals(Date.from(from), fromTerm.get("$gte"));
    assertEquals(Date.from(to), toTerm.get("$lte"));
    assertEquals(10000L, query.getValue().getMeta().getMaxTimeMsec());
    assertEquals(Boolean.TRUE, query.getValue().getMeta().getAllowDiskUse());
   }
  } finally { TimeZone.setDefault(previous); }
 }
}
