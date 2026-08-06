package com.flowgate.routing;

public record RouteDefinition(
        String id,          // unique name, e.g. "user-service"
        String pathPattern,  // Ant-style pattern, e.g. "/users/**"
        String targetUri     // e.g. "http://user-service:8080"
) {}