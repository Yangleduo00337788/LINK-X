package com.linkx.server.service;

public interface RateLimitService {

    void check(String key, int maxAttempts, int windowSeconds);
}
