package com.winten.greenlight.prototype.core.api.controller.queue;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.concurrent.TimeUnit;

@Repository
public class QueueRepository {

    private static final String QUEUE_KEY = "queue";
    private static final String ACTIVE_USERS_KEY = "activeUsers";
    private static final String CONCURRENT_USERS_KEY = "concurrentUsers";
    private static final String SESSION_PREFIX = "session:";

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    public void addToQueue(String queueId, double score) {
        redisTemplate.opsForZSet().add(QUEUE_KEY, queueId, score);
    }

    public void removeFromQueue(String queueId) {
        redisTemplate.opsForZSet().remove(QUEUE_KEY, queueId);
    }

    public void saveSession(String queueId, String token, long ttlMinutes) {
        redisTemplate.opsForValue().set(SESSION_PREFIX + queueId, token, ttlMinutes, TimeUnit.MINUTES);
    }

    public String getSession(String queueId) {
        return redisTemplate.opsForValue().get(SESSION_PREFIX + queueId);
    }

    public void deleteSession(String queueId) {
        redisTemplate.delete(SESSION_PREFIX + queueId);
    }

    public void addActiveUser(String queueId, long timestamp) {
        redisTemplate.opsForZSet().add(ACTIVE_USERS_KEY, queueId, timestamp);
        redisTemplate.opsForZSet().removeRangeByScore(ACTIVE_USERS_KEY, 0, timestamp - 300000);
    }

    public void addConcurrentUser(String queueId, long timestamp) {
        redisTemplate.opsForZSet().add(CONCURRENT_USERS_KEY, queueId, timestamp);
        redisTemplate.opsForZSet().removeRangeByScore(CONCURRENT_USERS_KEY, 0, timestamp - 300000);
    }

    public void removeActiveUser(String queueId) {
        redisTemplate.opsForZSet().remove(ACTIVE_USERS_KEY, queueId);
    }

    public void removeConcurrentUser(String queueId) {
        redisTemplate.opsForZSet().remove(CONCURRENT_USERS_KEY, queueId);
    }

    public Double getActiveScore(String queueId) {
        return redisTemplate.opsForZSet().score(ACTIVE_USERS_KEY, queueId);
    }

    public Long getConcurrentUserCount() {
        return redisTemplate.opsForZSet().zCard(CONCURRENT_USERS_KEY);
    }

    public Long getActiveUserCount() {
        return redisTemplate.opsForZSet().zCard(ACTIVE_USERS_KEY);
    }
}
