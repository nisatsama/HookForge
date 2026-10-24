package com.hookforge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

//@SpringBootApplication
@SpringBootApplication(exclude = {
		DataSourceAutoConfiguration.class,
		HibernateJpaAutoConfiguration.class
})
public class HookforgeApplication {

	public static void main(String[] args) {
		SpringApplication.run(HookforgeApplication.class, args);
	}

}
