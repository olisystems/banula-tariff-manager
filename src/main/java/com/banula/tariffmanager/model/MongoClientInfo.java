package com.banula.tariffmanager.model;

import com.banula.openlib.ocpi.model.ClientInfo;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@SuperBuilder
@NoArgsConstructor(force = true)
@Document("#{@MongoCollectionMapper.getHubClientInfoCollectionName()}")
public class MongoClientInfo extends ClientInfo {

    @Id
    private String mongoId;

    public String getMongoId() {
        return mongoId;
    }

    public void setMongoId(String mongoId) {
        this.mongoId = mongoId;
    }
}
