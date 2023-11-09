package com.example.demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {
	 @Bean
	    public ApplicationProperties applicationProperties() {
	        return new ApplicationProperties(); // You may need to provide initialization values here.
	    }
}
