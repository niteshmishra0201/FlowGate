package com.flowgate.routing;

import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
public class RouteMatcher {

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final List<RouteDefinition> sortedRoutes;
    private final HealthChecker healthChecker;

    public RouteMatcher(RouteProperties routeProperties, HealthChecker healthChecker) {
        this.healthChecker = healthChecker;
        this.sortedRoutes = routeProperties.routes().stream()
                .sorted(Comparator.comparing(RouteDefinition::pathPattern, pathMatcher.getPatternComparator("")))
                .toList();
    }

    public Optional<RouteDefinition> match(String path) {
        return sortedRoutes.stream()
                .filter(route -> pathMatcher.match(route.pathPattern(), path))
                .filter(route -> !healthChecker.getHealthyInstances(route).isEmpty()) // CHANGED
                .findFirst();
    }
}