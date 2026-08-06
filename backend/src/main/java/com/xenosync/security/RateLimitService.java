package com.xenosync.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token-bucket rate limiting (AUTH.md Section 10) — Bucket4j, in-memory now,
 * same "swap to Redis later if XenoSync ever runs multi-instance" caveat as
 * OAuthCodeService. Buckets are created lazily per key and never evicted —
 * acceptable for now, same class of tradeoff already accepted elsewhere in
 * this codebase (flagging, not fixing).
 */
@Service
public class RateLimitService {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public boolean tryConsume(String key, Bandwidth bandwidth) {
        Bucket bucket = buckets.computeIfAbsent(key, k -> Bucket.builder().addLimit(bandwidth).build());
        return bucket.tryConsume(1);
    }
}