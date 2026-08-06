package com.flowgate.ratelimit;

public record RateLimitResult(boolean allowed, long tokensRemaining) {}