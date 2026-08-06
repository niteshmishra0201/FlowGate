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

    public RouteMatcher(RouteProperties routeProperties) {
        // Sort once at startup: most specific pattern first.
        // AntPathMatcher's own comparator implements the specificity rules
        // discussed in Microstep 1 (more literal chars / fewer wildcards wins).
        this.sortedRoutes = routeProperties.routes().stream()
                .sorted(Comparator.comparing(
                        RouteDefinition::pathPattern,
                        pathMatcher.getPatternComparator("")
                ))
                .toList();
    }

    public Optional<RouteDefinition> match(String path) {
        return sortedRoutes.stream()
                .filter(route -> pathMatcher.match(route.pathPattern(), path))
                .findFirst(); // first match = most specific, thanks to the sort above
    }
}