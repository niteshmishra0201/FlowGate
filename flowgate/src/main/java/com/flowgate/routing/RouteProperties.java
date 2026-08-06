package com.flowgate.routing;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;

@ConfigurationProperties(prefix = "flowgate")
public record RouteProperties(List<RouteDefinition> routes) {}