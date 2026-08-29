package com.banula.tariffmanager.repository;

import com.banula.tariffmanager.model.MongoTariffPublicationOutbox;
import com.banula.tariffmanager.model.TariffPublicationStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface TariffPublicationOutboxRepository extends MongoRepository<MongoTariffPublicationOutbox, String> {

    List<MongoTariffPublicationOutbox> findByStatus(TariffPublicationStatus status);
}
