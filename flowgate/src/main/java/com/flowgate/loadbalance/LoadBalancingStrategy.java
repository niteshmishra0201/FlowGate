package com.flowgate.loadbalance;

import java.util.List;

public interface LoadBalancingStrategy {
    String selectInstance(String routeId, List<String> healthyInstances);
}