package com.flowgate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;

@SpringBootApplication
@Configuration
public class FlowgateApplication {

	public static void main(String[] args) {
		SpringApplication.run(FlowgateApplication.class, args);
	}

}
