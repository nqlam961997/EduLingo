package com.edulingo.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ImageCacheService {

    public record CachedImage(String base64Data, String mimeType, String topicId, Instant createdAt) {}

    private final Map<String, CachedImage> cache = new ConcurrentHashMap<>();

    public String store(String base64Data, String mimeType, String topicId) {
        String id = UUID.randomUUID().toString();
        cache.put(id, new CachedImage(base64Data, mimeType, topicId, Instant.now()));
        return id;
    }

    public CachedImage get(String imageId) {
        CachedImage img = cache.get(imageId);
        if (img == null) throw new IllegalArgumentException("Image not found: " + imageId);
        return img;
    }

    @Scheduled(fixedRate = 3600_000)
    void evictOld() {
        Instant cutoff = Instant.now().minusSeconds(7200);
        cache.entrySet().removeIf(e -> e.getValue().createdAt().isBefore(cutoff));
    }
}
