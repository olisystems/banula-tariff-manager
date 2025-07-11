package com.banula.tariffmanager.model.hash;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class HashVerifyResponse {
    @JsonProperty("verified")
    private boolean verified;

    @JsonProperty("transactionId")
    private String transactionId;
}
