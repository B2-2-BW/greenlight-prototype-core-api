package com.winten.greenlight.prototype.core.domain.token;

import com.winten.greenlight.prototype.core.db.repository.redis.token.TokenRepository;
import com.winten.greenlight.prototype.core.domain.customer.CustomerEntry;
import com.winten.greenlight.prototype.core.domain.action.Action;
import com.winten.greenlight.prototype.core.domain.customer.WaitStatus;
import com.winten.greenlight.prototype.core.support.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import java.util.Map;

/**
 * 토큰(JWT) 도메인의 비즈니스 로직을 담당하는 서비스입니다.
 * 토큰 발급, 만료, 유효성 검증 등의 기능을 제공합니다.
 * 위치: com.winten.greenlight.prototype.core.domain.token
 */
@Service
@RequiredArgsConstructor
public class TokenDomainService {
    private final TokenRepository tokenRepository;
    private final JwtUtil jwtUtil;

    /**
     * 새로운 JWT 토큰을 발급하고, 관련 메타데이터(고객 ID, 액션 ID, 상태 등)를 Redis에 저장합니다.
     * <b>(시나리오 1 적용)</b> 발급 전, 해당 고객과 액션에 대한 기존 토큰이 있다면 먼저 만료시킵니다.
     *
     * @param customerId 고객 ID
     * @param action     관련 Action 정보
     * @param status     토큰의 초기 상태 (예: "WAITING", "ALLOWED")
     * @return Mono<String> 발급된 JWT 토큰 문자열
     */
    public Mono<String> issueToken(String customerId, Action action, String status) {
        // 시나리오 1: 기존 토큰을 찾아 만료시키는 로직
        return findValidTokenJwt(customerId, action.getId())
            .flatMap(this::expireToken) // 기존 토큰이 있으면 만료시킨다.
            .then(Mono.defer(() -> {
                // 새로운 토큰 생성
                CustomerEntry entry = CustomerEntry.builder()
                    .actionId(action.getId())
                    .customerId(customerId)
                    .timestamp(System.currentTimeMillis())
                    .build();
                String jwt = jwtUtil.generateToken(entry);

                Map<String, String> metadata = Map.of(
                    "customerId", customerId,
                    "actionId", String.valueOf(action.getId()),
                    "status", status
                );

                // Redis에 토큰 메타데이터 저장 및 TTL 설정 (시나리오 2는 Repository에서 처리)
                return tokenRepository.saveTokenMetadata(jwt, metadata).thenReturn(jwt);
            }));
    }

    /**
     * 특정 JWT 토큰을 시스템에서 완전히 만료(무효화) 처리합니다.
     * 이 메서드는 Redis에 저장된 해당 토큰의 메타데이터를 삭제하여,
     * 더 이상 해당 토큰이 유효하지 않도록 만듭니다.
     *
     * @param jwt 만료 처리할 JWT 토큰 문자열
     * @return Mono<Void> 작업 완료 시그널
     */
    public Mono<Void> expireToken(String jwt) {
        // Redis에서 해당 토큰의 메타데이터를 삭제하여 토큰을 만료 처리합니다.
        return tokenRepository.deleteTokenMetadata(jwt);
    }

    /**
     * 고객 ID와 Action ID를 기반으로, Redis에서 유효한 JWT를 찾아 반환합니다.
     *
     * @param customerId 고객 ID
     * @param actionId   Action ID
     * @return Mono<String> 유효한 JWT 토큰 문자열 (없으면 Mono.empty())
     */
    public Mono<String> findValidTokenJwt(String customerId, Long actionId) {
        // Redis에 저장된 인덱스를 통해 JWT를 조회합니다.
        return tokenRepository.findJwtByCustomerIdAndActionId(customerId, actionId)
            .flatMap(jwt ->
                // 조회된 JWT가 JwtUtil을 통해 유효성 검증을 통과하는지 확인합니다.
                jwtUtil.validateToken(jwt) ? Mono.just(jwt) : Mono.empty());
    }
}
