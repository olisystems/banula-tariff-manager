package com.banula.tariffmanager.config;

import com.banula.openlib.ocpi.model.enums.converters.VersionNumberConverter;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ConverterConfigurator implements WebMvcConfigurer {
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(new VersionNumberConverter());
    }
}
