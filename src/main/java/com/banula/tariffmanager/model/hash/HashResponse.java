package com.banula.tariffmanager.model.hash;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class HashResponse {
    @JsonProperty("Transaction Hash")
    private String transactionHash;
}
