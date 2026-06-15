package com.banula.tariffmanager.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import com.banula.tariffmanager.model.OnChainTariff;

public interface TariffRepository extends MongoRepository<OnChainTariff, String> {

    @Query("""
    {
      'countryCode': ?0,
      'partyId': ?1,
      'id': ?2
    }
        """)
    Optional<OnChainTariff> findByCompositeKey(String countryCode, String partyId, String ocpiTariffId);

    @Query("{'partyId': ?0, 'countryCode': ?1, 'publishedAt': {$lte: ?2}}")
    List<OnChainTariff> findLatestTariffForPartyAtOrderByPublishedAt(String partyId, String countryCode,
            LocalDateTime datetime);

    Optional<OnChainTariff> findByHash(String hash);
}
