package com.winten.greenlight.prototype.core.api.controller.queue;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.winten.greenlight.prototype.core.support.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveZSetOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QueueServiceTest {

    @InjectMocks
    private QueueService queueService;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Mock
    private ReactiveZSetOperations<String, String> zSetOps;

    @Mock
    private DecodedJWT decodedJWT;

    private static final String ACTIVE_USERS_KEY = "activeUsers";

    @Test
    void testEnterQueueSuccess() {
        String actionId = "event123";
        String token = "generated.token";

        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        doReturn(token).when(jwtUtil).generateToken(anyString(), eq(actionId), anyLong());
        when(zSetOps.add(eq(ACTIVE_USERS_KEY), anyString(), anyDouble())).thenReturn(Mono.just(true));

        Mono<String> result = queueService.enterQueue(actionId);

        StepVerifier.create(result)
            .expectNext(token)
            .verifyComplete();

        verify(jwtUtil).generateToken(anyString(), eq(actionId), anyLong());
        verify(zSetOps).add(eq(ACTIVE_USERS_KEY), anyString(), anyDouble());
    }

    @Test
    void testEnterQueueWithEmptyActionId() {
        String actionId = "";
        String token = "generated.token";

        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        doReturn(token).when(jwtUtil).generateToken(anyString(), eq(actionId), anyLong());
        when(zSetOps.add(eq(ACTIVE_USERS_KEY), anyString(), anyDouble())).thenReturn(Mono.just(true));

        Mono<String> result = queueService.enterQueue(actionId);

        StepVerifier.create(result)
            .expectNext(token)
            .verifyComplete();

        verify(jwtUtil).generateToken(anyString(), eq(actionId), anyLong());
        verify(zSetOps).add(eq(ACTIVE_USERS_KEY), anyString(), anyDouble());
    }

    @Test
    void testEnterQueueRedisFailure() {
        String actionId = "event123";
        String token = "generated.token";

        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        doReturn(token).when(jwtUtil).generateToken(anyString(), eq(actionId), anyLong());
        when(zSetOps.add(eq(ACTIVE_USERS_KEY), anyString(), anyDouble()))
            .thenReturn(Mono.error(new RuntimeException("Redis failure")));

        Mono<String> result = queueService.enterQueue(actionId);

        StepVerifier.create(result)
            .expectErrorMatches(throwable -> throwable instanceof RuntimeException
                && throwable.getMessage().equals("Redis failure"))
            .verify();

        verify(jwtUtil).generateToken(anyString(), eq(actionId), anyLong());
        verify(zSetOps).add(eq(ACTIVE_USERS_KEY), anyString(), anyDouble());
    }

    @Test
    void testHeartbeatWithValidToken() {
        String token = "valid.token";
        String queueId = "queue123";

        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        when(jwtUtil.validateToken(token)).thenReturn(decodedJWT);
        when(decodedJWT.getSubject()).thenReturn(queueId);
        when(zSetOps.add(eq(ACTIVE_USERS_KEY), eq(queueId), anyDouble())).thenReturn(Mono.just(true));

        Mono<Void> result = queueService.heartbeat(token);

        StepVerifier.create(result)
            .verifyComplete();

        verify(jwtUtil).validateToken(token);
        verify(zSetOps).add(eq(ACTIVE_USERS_KEY), eq(queueId), anyDouble());
    }

    @Test
    void testHeartbeatWithInvalidToken() {
        String token = "invalid.token";

        when(jwtUtil.validateToken(token)).thenThrow(new JWTVerificationException("Invalid or expired token"));

        Mono<Void> result = queueService.heartbeat(token);

        StepVerifier.create(result)
            .consumeErrorWith(throwable -> {
                System.out.println("Exception: " + throwable.getClass() + ", Message: " + throwable.getMessage());
                assert throwable instanceof JWTVerificationException;
                assert throwable.getMessage().equals("Invalid or expired token");
            })
            .verify();

        verify(jwtUtil).validateToken(token);
        verify(zSetOps, never()).add(anyString(), anyString(), anyDouble());
    }

    @Test
    void testHeartbeatRedisFailure() {
        String token = "valid.token";
        String queueId = "queue123";

        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        when(jwtUtil.validateToken(token)).thenReturn(decodedJWT);
        when(decodedJWT.getSubject()).thenReturn(queueId);
        when(zSetOps.add(eq(ACTIVE_USERS_KEY), eq(queueId), anyDouble()))
            .thenReturn(Mono.error(new RuntimeException("Redis failure")));

        Mono<Void> result = queueService.heartbeat(token);

        StepVerifier.create(result)
            .expectErrorMatches(throwable -> throwable instanceof RuntimeException
                && throwable.getMessage().equals("Redis failure"))
            .verify();

        verify(jwtUtil).validateToken(token);
        verify(zSetOps).add(eq(ACTIVE_USERS_KEY), eq(queueId), anyDouble());
    }
}
