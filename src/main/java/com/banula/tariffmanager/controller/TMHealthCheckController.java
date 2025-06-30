package com.banula.tariffmanager.controller;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${party.api-non-ocpi-prefix}/health-check")
public class TMHealthCheckController {

    @GetMapping
    public String healthCheck() {
        return "Service is up and running";
    }

}
