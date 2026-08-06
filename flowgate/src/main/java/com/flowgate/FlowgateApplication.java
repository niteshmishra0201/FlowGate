package com.flowgate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.flowgate.routing.RouteProperties;

@SpringBootApplication
@Configuration
@EnableConfigurationProperties(RouteProperties.class)

public class FlowgateApplication {

	public static void main(String[] args) {
		SpringApplication.run(FlowgateApplication.class, args);
	}

}
