package com.winten.greenlight.prototype.core.db.repository.redis.action;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.winten.greenlight.prototype.core.domain.action.Action;
import com.winten.greenlight.prototype.core.domain.action.ActionGroup;
import com.winten.greenlight.prototype.core.support.error.CoreException;
import com.winten.greenlight.prototype.core.support.error.ErrorType;
import com.winten.greenlight.prototype.core.support.util.RedisKeyBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@Repository
@RequiredArgsConstructor
public class ActionRepository {
    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final RedisKeyBuilder keyBuilder;
    private final ObjectMapper objectMapper;

    private static final String ACTION_KEY_PREFIX = "action:url:";

    public Mono<Action> findByUrl(String url) {
        String key = ACTION_KEY_PREFIX + url;
        return redisTemplate.opsForValue().get(key)
            .flatMap(json -> deserializeToAction(String.valueOf(json))) // <-- 메소드 참조 대신 람다 표현식 사용
            .doOnError(e -> log.error("Failed to find or parse Action for url: {}", url, e));
    }

    public Mono<Action> getActionById(Long actionId) {
        String key = keyBuilder.action(actionId);

        return redisTemplate.opsForHash().entries(key)
                .collectMap(entry -> (String) entry.getKey(), Map.Entry::getValue)
                .flatMap(map -> map.isEmpty()
                        ? Mono.error(CoreException.of(ErrorType.ACTION_NOT_FOUND, "Action을 찾을 수 없습니다. actionId: " + actionId))
                        : Mono.just(objectMapper.convertValue(map, Action.class))
                );
    }

    public Mono<ActionGroup> getActionGroupById(Long actionGroupId) {
        String key = keyBuilder.actionGroupMeta(actionGroupId);
        return redisTemplate.opsForHash().entries(key)
                .collectMap(entry -> (String) entry.getKey(), Map.Entry::getValue)
                .flatMap(map -> map.isEmpty()
                        ? Mono.error(CoreException.of(ErrorType.ACTION_GROUP_NOT_FOUND, "Action Group을 찾을 수 없습니다. actionGroupId: " + actionGroupId))
                        : Mono.just(objectMapper.convertValue(map, ActionGroup.class))
                );
    }

    private Mono<Action> deserializeToAction(String json) {
        try {
            return Mono.just(objectMapper.readValue(json, Action.class));
        } catch (JsonProcessingException e) {
            return Mono.error(new RuntimeException("Failed to deserialize Action from JSON", e));
        }
    }
}