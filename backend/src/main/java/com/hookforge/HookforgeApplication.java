
package com.hookforge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class HookforgeApplication {

	public static void main(String[] args) {
		SpringApplication.run(HookforgeApplication.class, args);
	}
}
