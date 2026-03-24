package com.ctps.ctps_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CtpsApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(CtpsApiApplication.class, args);
	}

}
