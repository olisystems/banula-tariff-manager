package com.banula.tariffmanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = { "com.banula.openlib", "com.olisystems.privatelib", "com.banula" })
public class TMBackendApplication {
	public static void main(String[] args) {
		SpringApplication.run(TMBackendApplication.class, args);
	}
}
