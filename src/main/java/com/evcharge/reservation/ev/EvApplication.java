package com.evcharge.reservation.ev;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EvApplication {

	public static void main(String[] args) {
		SpringApplication.run(EvApplication.class, args);
	}
}
