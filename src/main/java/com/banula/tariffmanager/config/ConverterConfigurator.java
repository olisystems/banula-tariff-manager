package com.banula.tariffmanager.config;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.banula.openlib.ocpi.model.enums.converters.VersionNumberConverter;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@Configuration
public class ConverterConfigurator implements WebMvcConfigurer {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.configure(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE, false);
        return objectMapper;
    }

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(new VersionNumberConverter());
        registry.addConverter(new StringToLocalDateTimeConverter());
    }

    /**
     * Custom converter to convert String to LocalDateTime.
     * Per OCPI standard, all timestamps are treated as UTC:
     * - "2026-04-09T12:00:00Z" -> 12:00 UTC
     * - "2026-04-09T12:00:00" -> 12:00 UTC (assumes UTC if no timezone specified)
     */
    public static class StringToLocalDateTimeConverter implements Converter<String, LocalDateTime> {
        @Override
        public LocalDateTime convert(String source) {
            if (source == null || source.trim().isEmpty()) {
                return null;
            }
            try {
                // Try parsing with timezone offset first (e.g., "Z" or "+00:00")
                try {
                    OffsetDateTime odt = OffsetDateTime.parse(source, DateTimeFormatter.ISO_DATE_TIME);
                    // Convert to UTC zone to ensure consistency
                    return odt.atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
                } catch (DateTimeParseException ignored) {
                    // If no timezone, parse as LocalDateTime and treat as UTC
                    return LocalDateTime.parse(source, DATE_FORMATTER);
                }
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException(
                        "Invalid date format. Expected ISO 8601 format (yyyy-MM-ddTHH:mm:ss or yyyy-MM-ddTHH:mm:ssZ): "
                                + source);
            }
        }
    }
}
