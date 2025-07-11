package com.banula.tariffmanager.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("MongoCollectionMapper")
@Getter
public class MongoCollectionMapper {
    private final String tariffCollectionName;
    private final String ocnCredentialsCollectionName;

    @Autowired
    public MongoCollectionMapper(ApplicationConfiguration config) {
        String prefix = config.getCollectionPrefix();
        this.tariffCollectionName = prefix + "OnChainTariff";
        this.ocnCredentialsCollectionName = prefix + "OcnCredentials";

    }
}
