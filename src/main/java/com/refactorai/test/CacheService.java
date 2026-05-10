package com.refactorai.test;

import java.util.HashMap;
import java.util.Map;

public class CacheService {
    private Map<String, Object> cache = new HashMap<>();
    private Map<String, Long> cacheTimestamps = new HashMap<>();
    private final ConfigurationService configService;
    private final LoggingService loggingService;

    public CacheService(ConfigurationService configService, LoggingService loggingService) {
        this.configService = configService;
        this.loggingService = loggingService;
    }

    public Object getFromCache(String key) {
        if (!cache.containsKey(key)) return null;
        Long ts = cacheTimestamps.get(key);
        if (ts == null || System.currentTimeMillis() - ts > configService.getCacheExpiry()) {
            cache.remove(key); cacheTimestamps.remove(key); return null;
        }
        return cache.get(key);
    }

    public void putInCache(String key, Object value) {
        cache.put(key, value); cacheTimestamps.put(key, System.currentTimeMillis());
    }

    public void invalidateCache(String key) { cache.remove(key); cacheTimestamps.remove(key); }

    public void clearAllCache() {
        cache.clear();
        cacheTimestamps.clear();
        loggingService.log("INFO", "Cache cleared");
    }
}
