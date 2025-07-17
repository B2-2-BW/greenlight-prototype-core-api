package com.winten.greenlight.prototype.core.db.repository.redis.token;

import com.winten.greenlight.prototype.core.support.util.RedisKeyBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/**
 * 토큰(JWT) 관련 Redis 작업을 수행하는 Repository 클래스입니다.
 * Redis Hash를 사용하여 토큰 메타데이터를 저장하고 관리합니다.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class TokenRepository {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final RedisKeyBuilder keyBuilder;
    private static final Duration TOKEN_TTL = Duration.ofHours(1);

    private static final String TOKEN_METADATA_KEY_PREFIX = "token:metadata:";
    private static final String CUSTOMER_ACTION_TO_JWT_KEY_PREFIX = "idx:customer-action-to-jwt:";

    // 상태별 TTL 상수 정의
    private static final Duration ALLOWED_TOKEN_TTL = Duration.ofMinutes(5);
    private static final Duration WAITING_TOKEN_TTL = Duration.ofHours(2);

    /**
     * JWT 토큰의 메타데이터를 Redis Hash로 저장합니다.
     * 토큰의 유효 기간(TTL)도 함께 설정합니다.
     * 토큰 메타데이터를 저장하고, 상태에 따라 TTL을 설정합니다.
     *
     * @param jwt      저장할 JWT 문자열 (Redis Key)
     * @param metadata 저장할 메타데이터 (Hash Fields: customerId, actionId, status 등)
     * @return Mono<Boolean> 저장 성공 여부
     */
    public Mono<Void> saveTokenMetadata(String jwt, Map<String, String> metadata) {
        String key = TOKEN_METADATA_KEY_PREFIX + jwt;
        String status = metadata.get("status");
        Duration ttl = "ALLOWED".equals(status) ? ALLOWED_TOKEN_TTL : WAITING_TOKEN_TTL;

        String customerId = metadata.get("customerId");
        String actionId = metadata.get("actionId");
        String indexKey = CUSTOMER_ACTION_TO_JWT_KEY_PREFIX + customerId + ":" + actionId;

        return redisTemplate.opsForHash().putAll(key, metadata)
            .then(redisTemplate.expire(key, ttl)) // 메타데이터에 TTL 설정
            .then(redisTemplate.opsForValue().set(indexKey, jwt))
            .then(redisTemplate.expire(indexKey, ttl)) // 인덱스에도 동일한 TTL 설정
            .then();
    }

    /**
     * Redis에 저장된 토큰의 상태(status) 필드만 업데이트합니다.
     *
     * @param jwt    업데이트할 JWT 문자열
     * @param status 변경할 상태 값 (예: "EXPIRED", "ENTERED")
     * @return Mono<Void> 작업 완료 시그널
     */
    public Mono<Void> updateTokenStatus(String jwt, String status) {
        String key = keyBuilder.token(jwt);
        ReactiveHashOperations<String, String, String> hashOps = redisTemplate.opsForHash();
        return hashOps.put(key, "status", status).then(); // "status" 필드만 업데이트
    }

    /**
     * Redis에 저장된 토큰의 상태(status) 필드를 조회합니다.
     *
     * @param jwt 조회할 JWT 문자열
     * @return Mono<String> 상태 값을 담은 Mono
     */
    public Mono<String> getTokenStatus(String jwt) {
        String key = keyBuilder.token(jwt);
        ReactiveHashOperations<String, String, String> hashOps = redisTemplate.opsForHash();
        return hashOps.get(key, "status"); // "status" 필드 값 조회
    }

    /**
     * 고객 ID와 Action ID를 기반으로 유효한 JWT를 찾습니다.
     * 이 로직은 Redis에 별도의 인덱스(예: String 타입의 "customer:{customerId}:action:{actionId}" -> JWT)가
     * 미리 저장되어 있어야 합니다. (JWT 발급 시 함께 저장되어야 함)
     *
     * @param customerId 고객 ID
     * @param actionId   Action ID
     * @return Mono<String> 찾은 JWT 문자열 (없으면 Mono.empty())
     */

    public Mono<String> findJwtByCustomerIdAndActionId(String customerId, Long actionId) {
        String indexKey = CUSTOMER_ACTION_TO_JWT_KEY_PREFIX + customerId + ":" + actionId;
        return redisTemplate.opsForValue().get(indexKey);
    }

    /**
     * Redis에서 특정 JWT 토큰과 관련된 모든 메타데이터를 삭제합니다.
     *
     * @param jwt 삭제할 JWT 문자열
     * @return Mono<Long> 삭제된 키의 개수 (1이면 성공, 0이면 해당 키 없음)
     */
    public Mono<Void> deleteTokenMetadata(String jwt) {
        String key = TOKEN_METADATA_KEY_PREFIX + jwt;
        // 먼저 메타데이터를 조회해서 customerId와 actionId를 얻어 인덱스를 삭제해야 함
        return getTokenMetadata(jwt)
            .flatMap(metadata -> {
                // Redis Hash의 필드 값은 Object 타입이므로 String으로 캐스팅이 필요합니다.
                String customerId = (String) metadata.get("customerId");
                String actionId = (String) metadata.get("actionId");

                // 만약의 경우를 대비해 null 체크
                if (customerId == null || actionId == null) {
                    // 인덱스 키를 만들 수 없으므로, 메타데이터 키만 삭제 시도
                    return redisTemplate.delete(key).then();
                }

                String indexKey = CUSTOMER_ACTION_TO_JWT_KEY_PREFIX + customerId + ":" + actionId;
                // 메타데이터와 인덱스를 모두 삭제
                return redisTemplate.delete(key, indexKey).then();
            });
    }

    public Mono<Map<Object, Object>> getTokenMetadata(String jwt) {
        String key = TOKEN_METADATA_KEY_PREFIX + jwt;
        return redisTemplate.opsForHash().entries(key)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

}
