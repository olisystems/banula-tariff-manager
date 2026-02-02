package com.banula.tariffmanager.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/health")
public class TMHealthCheckController {

    @GetMapping
    public String healthCheck() {
        return "Service is up and running";
    }

}
