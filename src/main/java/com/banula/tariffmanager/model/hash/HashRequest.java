package com.banula.tariffmanager.model.hash;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class HashRequest {
    @JsonProperty("Payload")
    private String payload;
    @JsonProperty("Tags")
    private String tags;
}
