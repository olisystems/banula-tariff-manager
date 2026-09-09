package com.banula.tariffmanager.repository;

import com.banula.tariffmanager.model.MongoTariffPublicationOutbox;
import com.banula.tariffmanager.model.TariffPublicationStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;

public interface TariffPublicationOutboxRepository extends MongoRepository<MongoTariffPublicationOutbox, String> {

    Optional<MongoTariffPublicationOutbox> findByCountryCodeAndPartyIdAndTariffId(String countryCode, String partyId, String tariffId);

    @Query("{'status': 'PENDING', '$or': [{'nextAttemptAt': null}, {'nextAttemptAt': {'$lte': ?0}}]}")
    List<MongoTariffPublicationOutbox> findDue(LocalDateTime now, Pageable pageable);
}
