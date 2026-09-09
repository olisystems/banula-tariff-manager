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
  var result = service.search("tariffs", null, "a.*", null, "de", "oli", null, null, 10, 10);
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
  assertThrows(ResponseStatusException.class, () -> service.search("tariffs", null, null,null,null,null,null,null,-1,10));
  assertThrows(ResponseStatusException.class, () -> service.search("tariffs", null, null,null,null,null,null,null,0,201));
  assertThrows(ResponseStatusException.class, () -> service.search("tariffs", null, null,null,"Germany",null,null,null,0,10));
  assertThrows(ResponseStatusException.class, () -> service.search("unknown", null, null,null,null,null,null,null,0,10));
  verifyNoInteractions(mongo);
 }
}
