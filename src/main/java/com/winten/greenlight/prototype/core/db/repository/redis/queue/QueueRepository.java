package com.winten.greenlight.prototype.core.db.repository.redis.queue;

import com.winten.greenlight.prototype.core.support.util.RedisKeyBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * 대기열 관련 Redis 작업을 수행하는 Repository 클래스입니다.
 * Redis Sorted Set (ZSET)을 사용하여 대기열 및 활성 사용자 수를 관리합니다.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class QueueRepository {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final RedisKeyBuilder keyBuilder;

    /**
     * 특정 ActionGroup의 활성 사용자 수를 조회합니다.
     * Redis ZSET의 cardinality (요소 개수)를 사용합니다.
     *
     * @param actionGroupId 조회할 ActionGroup의 ID
     * @return Mono<Long> 활성 사용자 수를 담은 Mono
     */
    public Mono<Long> getActiveUserCount(Long actionGroupId) {
        String key = keyBuilder.activeUsers(actionGroupId); // "active_users:{actionGroupId}" 키 생성
        return redisTemplate.opsForZSet().size(key); // ZSET의 요소 개수 반환
    }

    /**
     * 사용자를 대기열(Waiting Queue)에 추가합니다.
     * Redis ZSET의 ADD 명령어를 사용하며, 현재 시간을 score로 사용하여 순서를 결정합니다.
     *
     * @param actionId 사용자가 진입하려는 Action의 ID
     * @param queueId  사용자에게 부여된 고유 대기 ID (JWT의 subject 등)
     * @param score    대기열 순서를 결정하는 점수 (예: System.currentTimeMillis())
     * @return Mono<Long> 대기열에 추가된 후의 순번 (0부터 시작)
     */
    public Mono<Long> addToWaitingQueue(Long actionId, String queueId, double score) {
        String key = keyBuilder.waitingQueue(actionId); // "queue:{actionId}:waiting" 키 생성
        return redisTemplate.opsForZSet().add(key, queueId, score)
            .flatMap(added -> added ? redisTemplate.opsForZSet().rank(key, queueId) : Mono.just(-1L)); // 추가 성공 시 순위 반환, 실패 시 -1L
    }

    /**
     * 대기열에서 특정 사용자의 순번을 조회합니다.
     *
     * @param actionId 조회할 Action의 ID
     * @param queueId  조회할 사용자의 고유 대기 ID
     * @return Mono<Long> 대기 순번 (0부터 시작)
     */
    public Mono<Long> getRankFromWaitingQueue(Long actionId, String queueId) {
        String key = keyBuilder.waitingQueue(actionId);
        return redisTemplate.opsForZSet().rank(key, queueId);
    }
}
