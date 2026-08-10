package com.banula.tariffmanager.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.mapping.event.ValidatingMongoEventListener;

import jakarta.validation.Validator;

@Configuration
public class MongoValidationConfig {

    @Bean
    public ValidatingMongoEventListener validatingMongoEventListener(Validator validator) {
        return new ValidatingMongoEventListener(validator);
    }
}
