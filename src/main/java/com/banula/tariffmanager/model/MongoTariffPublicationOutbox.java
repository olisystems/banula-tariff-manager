package com.banula.tariffmanager.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Document("#{@MongoCollectionMapper.getTariffPublicationOutboxCollectionName()}")
@CompoundIndex(name = "unique_tariff_publication", def = "{'countryCode': 1, 'partyId': 1, 'tariffId': 1}", unique = true)
public class MongoTariffPublicationOutbox {

    @Id
    private String mongoId;
    private String countryCode;
    private String partyId;
    private String tariffId;
    private TariffPublicationStatus status;
    private LocalDateTime lastAttemptAt;
    private String lastError;
}
