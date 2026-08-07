package com.flowgate.routing;

import java.util.List;

public record RouteDefinition(
        String id, String pathPattern, List<String> targetUris,
        String healthCheckPath, String loadBalancingStrategy
) {}