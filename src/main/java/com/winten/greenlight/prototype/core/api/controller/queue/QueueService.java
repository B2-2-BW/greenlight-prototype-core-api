package com.winten.greenlight.prototype.core.api.controller.queue;

import java.util.UUID;
import com.winten.greenlight.prototype.core.support.util.JwtUtil;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class QueueService {
    private final JwtUtil jwtUtil;
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private static final String ACTIVE_USERS_KEY = "activeUsers";

    public QueueService(JwtUtil jwtUtil, ReactiveRedisTemplate<String, String> redisTemplate) {
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
    }

    public Mono<String> enterQueue(String actionId) {
        String queueId = UUID.randomUUID().toString();
        long score = System.currentTimeMillis();
        String token = jwtUtil.generateToken(queueId, actionId, score);

        return redisTemplate.opsForZSet()
            .add(ACTIVE_USERS_KEY, queueId, score)
            .then(Mono.just(token));
    }

    /**
     * Mono.defer() 로 감싸기
     * Mono.defer() 는 내부 코드 실행을 구독 시점으로 미루기 때문에
     * 예외가 발생해도 Mono.error() 형태로 안전하게 흘러감
     * */
    public Mono<Void> heartbeat(String token) {
        return Mono.defer(() -> {
            String queueId = jwtUtil.validateToken(token).getSubject();
            return redisTemplate.opsForZSet()
                .add(ACTIVE_USERS_KEY, queueId, System.currentTimeMillis())
                .then();
        });
    }

}
