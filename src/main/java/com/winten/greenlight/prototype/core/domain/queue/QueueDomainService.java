package com.winten.greenlight.prototype.core.domain.queue;

import com.winten.greenlight.prototype.core.db.repository.redis.queue.QueueRepository;
import com.winten.greenlight.prototype.core.domain.action.ActionGroup;
import com.winten.greenlight.prototype.core.domain.action.CachedActionService;
import com.winten.greenlight.prototype.core.domain.customer.WaitStatus;
import com.winten.greenlight.prototype.core.domain.token.TokenDomainService;
import com.winten.greenlight.prototype.core.support.util.RedisKeyBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 대기열(Queue) 도메인의 비즈니스 로직을 담당하는 서비스입니다.
 * 대기열 진입 여부 판단, 사용자 대기열 추가, 순번 조회 등의 기능을 제공합니다.
 * 위치: com.winten.greenlight.prototype.core.domain.queue
 */
@Service
@RequiredArgsConstructor
public class QueueDomainService {
    private final QueueRepository queueRepository;
    private final CachedActionService cachedActionService; // ActionGroup 정보를 위해 주입
    private final TokenDomainService tokenDomainService;
    private final RedisKeyBuilder redisKeyBuilder;

    /**
     * 특정 ActionGroup에 대해 현재 대기가 필요한지 판단합니다.
     * 활성 사용자 수가 ActionGroup의 최대 허용 고객 수를 초과하는지 확인합니다.
     *
     * @param actionGroupId 검사할 ActionGroup의 ID
     * @return Mono<Boolean> 대기 필요 여부
     */
    public Mono<Boolean> isWaitingRequired(Long actionGroupId) {
        Mono<Long> activeUserCountMono = queueRepository.getActiveUserCount(actionGroupId);
        // ActionGroup 정보를 CachedActionService를 통해 가져옵니다.
        Mono<Integer> maxAllowedCustomersMono = cachedActionService.getActionGroupById(actionGroupId)
            .map(ActionGroup::getMaxActiveCustomers)
            .defaultIfEmpty(0);

        return Mono.zip(activeUserCountMono, maxAllowedCustomersMono)
            .map(tuple -> tuple.getT1() >= tuple.getT2());
    }

    /**
     * 사용자를 지정된 상태의 대기열(Redis Sorted Set)에 추가합니다.
     *
     * @param actionGroupId 사용자가 진입하려는 ActionGroup의 ID
     * @param customerId  사용자에게 부여된 고유 ID
     * @param status 대기열의 상태 (WAITING or READY)
     * @return Mono<Long> 대기열에 추가된 후의 순번 (0부터 시작)
     */
    public Mono<Long> addUserToQueue(Long actionGroupId, String customerId, WaitStatus status) {
        String queueKey = redisKeyBuilder.queue(actionGroupId, status);
        return queueRepository.add(queueKey, customerId, System.currentTimeMillis());
    }

    /**ㄷ
     * 대기열에서 특정 사용자의 현재 순번을 조회합니다.
     *
     * @param actionId 조회할 Action의 ID
     * @param queueId  조회할 사용자의 고유 대기 ID
     * @return Mono<Long> 대기 순번 (0부터 시작)
     */
    public Mono<Long> getQueueRank(Long actionId, String queueId) {
        return queueRepository.getRankFromWaitingQueue(actionId, queueId);
    }

    /**
     * 사용자가 대기열을 거쳐 서비스에 최종 진입했을 때 호출됩니다.
     * 해당 사용자의 토큰을 만료 처리합니다.
     *
     * @param customerId 고객 ID
     * @param actionId   액션 ID
     * @param token      만료 처리할 JWT 토큰
     * @return Mono<Void> 작업 완료 시그널
     */
    public Mono<Void> grantServiceAccess(String customerId, Long actionId, String token) {
        // TODO: 실제 서비스 접근 권한 부여 로직 (예: 세션 생성, DB 상태 업데이트 등)
        System.out.println("Granting service access to customer " + customerId + " for action " + actionId);

        // 토큰 만료 처리
        return tokenDomainService.expireToken(token)
            .doOnSuccess(v -> System.out.println("User " + customerId + " entered service. Token " + token + " expired."))
            .then();
    }
    /**
     * 사용자가 대기열에서 이탈(취소)했을 때 호출됩니다.
     * 대기열에서 사용자를 제거하고 해당 토큰을 만료 처리합니다.
     *
     * @param customerId 고객 ID
     * @param token      만료 처리할 JWT 토큰
     * @return Mono<Void> 작업 완료 시그널
     */
    public Mono<Void> removeUserFromQueue(String customerId, String token) {
        // TODO: 실제 대기열에서 사용자 제거 로직 (예: Redis Sorted Set에서 제거)
        System.out.println("Removing customer " + customerId + " from queue.");

        // 토큰 만료 처리
        return tokenDomainService.expireToken(token)
            .doOnSuccess(v -> System.out.println("User " + customerId + " left queue. Token " + token + " expired."))
            .then();
    }
}

