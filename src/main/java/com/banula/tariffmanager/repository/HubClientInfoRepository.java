package com.banula.tariffmanager.repository;

import com.banula.openlib.ocpi.model.enums.Role;
import com.banula.tariffmanager.model.MongoClientInfo;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface HubClientInfoRepository extends MongoRepository<MongoClientInfo, String> {

    List<MongoClientInfo> findByPartyIdAndCountryCode(String partyId, String countryCode);

    Optional<MongoClientInfo> findByPartyIdAndCountryCodeAndRole(String partyId, String countryCode, Role role);
}
