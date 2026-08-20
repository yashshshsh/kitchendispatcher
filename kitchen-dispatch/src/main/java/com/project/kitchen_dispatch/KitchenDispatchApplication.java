package com.project.kitchen_dispatch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class KitchenDispatchApplication {

	public static void main(String[] args) {

		SpringApplication.run(
				KitchenDispatchApplication.class,
				args
		);
	}
}