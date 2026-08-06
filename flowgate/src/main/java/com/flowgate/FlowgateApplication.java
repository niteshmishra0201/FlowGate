package com.flowgate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.flowgate.routing.RouteProperties;
import org.springframework.scheduling.annotation.EnableScheduling;


@SpringBootApplication
@Configuration
@EnableConfigurationProperties(RouteProperties.class)
@EnableScheduling   


public class FlowgateApplication {

	public static void main(String[] args) {
		SpringApplication.run(FlowgateApplication.class, args);
	}

}
