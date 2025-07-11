package com.banula.tariffmanager.model.hash;

import com.banula.openlib.ocpi.model.dto.TariffDTO;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class HashVerifyRequest {
    @JsonProperty("tariff")
    private TariffDTO tariff;
    @JsonProperty("hash")
    private String hash;
}
