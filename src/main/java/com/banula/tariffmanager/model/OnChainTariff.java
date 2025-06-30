package com.banula.tariffmanager.model;

import com.banula.openlib.ocpi.model.Tariff;
import com.banula.openlib.web3.utils.Hashable;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;
import java.time.LocalDateTime;

@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Document("#{@MongoCollectionMapper.getTariffCollectionName()}")
public class OnChainTariff extends Tariff implements Serializable, Hashable {

    @Id
    @JsonProperty("hashTag")
    String hashTag;
    @JsonProperty("hash")
    String hash;
    @JsonProperty("transactionId")
    String transactionId;
    @JsonProperty("publishedAt")
    LocalDateTime publishedAt;

    @Override
    public String encode() {
        StringBuilder builder = new StringBuilder();

        // Append required fields
        builder.append(this.getCountryCode());
        builder.append(this.getPartyId());
        builder.append(this.getId());
        builder.append(this.getCurrency());

        // Append type with toString(), handle null case
        if (this.getType() != null) {
            builder.append(this.getType().toString());
        }

        // Handle TariffAltText list, handle null case
        if (this.getTariffAltText() != null) {
            this.getTariffAltText().forEach(altText -> {
                if (altText != null) {
                    builder.append(altText.getLanguage());
                    builder.append(altText.getText());
                }
            });
        }

        // Append tariffAltUrl, handle null case
        builder.append(this.getTariffAltUrl() != null ? this.getTariffAltUrl() : "");

        // Handle MinPrice and MaxPrice, handle null case
        if (this.getMinPrice() != null) {
            builder.append(this.getMinPrice().getExclVat().toString());
            builder.append(this.getMinPrice().getInclVat().toString());
        }
        if (this.getMaxPrice() != null) {
            builder.append(this.getMaxPrice().getExclVat().toString());
            builder.append(this.getMaxPrice().getInclVat().toString());
        }

        // Handle elements list, handle null case
        if (this.getElements() != null) {
            this.getElements().forEach(element -> {
                if (element != null) {
                    // Handle PriceComponents list, handle null case
                    if (element.getPriceComponents() != null) {
                        element.getPriceComponents().forEach(priceComponent -> {
                            if (priceComponent != null) {
                                builder.append(priceComponent.getType().toString());
                                builder.append(priceComponent.getPrice());
                                builder.append(priceComponent.getVat());
                                builder.append(priceComponent.getStepSize());
                            }
                        });
                    }

                    // Handle Restrictions, handle null case
                    if (element.getRestrictions() != null) {
                        builder.append(element.getRestrictions().getStartTime() != null
                                ? element.getRestrictions().getStartTime()
                                : "");
                        builder.append(
                                element.getRestrictions().getEndTime() != null ? element.getRestrictions().getEndTime()
                                        : "");
                        builder.append(element.getRestrictions().getStartDate() != null
                                ? element.getRestrictions().getStartDate()
                                : "");
                        builder.append(
                                element.getRestrictions().getEndDate() != null ? element.getRestrictions().getEndDate()
                                        : "");
                        builder.append(
                                element.getRestrictions().getMinKwh() != null ? element.getRestrictions().getMinKwh()
                                        : "");
                        builder.append(
                                element.getRestrictions().getMaxKwh() != null ? element.getRestrictions().getMaxKwh()
                                        : "");
                        builder.append(element.getRestrictions().getMinCurrent() != null
                                ? element.getRestrictions().getMinCurrent()
                                : "");
                        builder.append(element.getRestrictions().getMaxCurrent() != null
                                ? element.getRestrictions().getMaxCurrent()
                                : "");
                        builder.append(element.getRestrictions().getMinPower() != null
                                ? element.getRestrictions().getMinPower()
                                : "");
                        builder.append(element.getRestrictions().getMaxPower() != null
                                ? element.getRestrictions().getMaxPower()
                                : "");
                        builder.append(element.getRestrictions().getMinDuration() != null
                                ? element.getRestrictions().getMinDuration()
                                : "");
                        builder.append(element.getRestrictions().getMaxDuration() != null
                                ? element.getRestrictions().getMaxDuration()
                                : "");

                        // Handle DayOfWeek list, handle null case
                        if (element.getRestrictions().getDayOfWeek() != null) {
                            element.getRestrictions().getDayOfWeek().forEach(weekday -> {
                                if (weekday != null) {
                                    builder.append(weekday);
                                }
                            });
                        }

                        builder.append(element.getRestrictions().getReservation() != null
                                ? element.getRestrictions().getReservation()
                                : "");
                    }
                }
            });
        }

        // Append startDateTime and endDateTime
        builder.append(this.getStartDateTime() != null ? this.getStartDateTime().toString() : "");
        builder.append(this.getEndDateTime() != null ? this.getEndDateTime().toString() : "");

        // Handle EnergyMix, handle null case
        if (this.getEnergyMix() != null) {
            builder.append(this.getEnergyMix().isGreenEnergy());
            builder.append(this.getEnergyMix().getSupplierName() != null ? this.getEnergyMix().getSupplierName() : "");
            builder.append(
                    this.getEnergyMix().getEnergyProductName() != null ? this.getEnergyMix().getEnergyProductName()
                            : "");

            // Handle EnergySources list, handle null case
            if (this.getEnergyMix().getEnergySources() != null) {
                this.getEnergyMix().getEnergySources().forEach(energySource -> {
                    if (energySource != null) {
                        builder.append(energySource.getPercentage() != null ? energySource.getPercentage() : "");
                        builder.append(energySource.getSource() != null ? energySource.getSource() : "");
                    }
                });
            }

            // Handle EnvironImpact list, handle null case
            if (this.getEnergyMix().getEnvironImpact() != null) {
                this.getEnergyMix().getEnvironImpact().forEach(environImpact -> {
                    if (environImpact != null) {
                        builder.append(environImpact.getCategory() != null ? environImpact.getCategory() : "");
                        builder.append(environImpact.getAmount() != null ? environImpact.getAmount() : "");
                    }
                });
            }
        }

        // Append lastUpdated, handle null case
        builder.append(this.getLastUpdated() != null ? this.getLastUpdated().toString() : "");

        return builder.toString();
    }

}
