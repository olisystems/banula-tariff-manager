package com.banula.tariffmanager.model.hash;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class PublishedHashResponse {
    @JsonProperty("value")
    String value;
}
