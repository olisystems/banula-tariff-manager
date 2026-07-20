package com.banula.tariffmanager.model.dto;

import com.banula.openlib.ocpi.model.enums.ConnectionStatus;
import com.banula.openlib.ocpi.model.enums.Role;
import com.banula.openlib.ocpi.util.OCPILocalDateTimeDeserializer;
import com.banula.openlib.ocpi.util.OCPILocalDateTimeSerializer;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HubClientInfoDTO {

    private String id;

    @JsonProperty("party_id")
    private String partyId;
    @JsonProperty("country_code")
    private String countryCode;
    private Role role;
    private ConnectionStatus status;

    @JsonProperty("last_updated")
    @JsonDeserialize(using = OCPILocalDateTimeDeserializer.class)
    @JsonSerialize(using = OCPILocalDateTimeSerializer.class)
    private LocalDateTime lastUpdated;
}
