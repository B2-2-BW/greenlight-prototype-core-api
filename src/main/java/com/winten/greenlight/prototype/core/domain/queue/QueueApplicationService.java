
package com.winten.greenlight.prototype.core.domain.queue;

import com.winten.greenlight.prototype.core.api.controller.queue.dto.CheckOrEnterResponse;
import com.winten.greenlight.prototype.core.domain.action.Action;
import com.winten.greenlight.prototype.core.domain.action.ActionDomainService;
import com.winten.greenlight.prototype.core.domain.customer.WaitStatus;
import com.winten.greenlight.prototype.core.domain.token.TokenDomainService;
import com.winten.greenlight.prototype.core.support.util.RuleMatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 대기열 시스템의 핵심 애플리케이션 서비스입니다.
 * 클라이언트의 요청을 받아 여러 도메인 서비스(Action, Queue, Token)를 조율하여
 * 대기열 진입 및 토큰 발급과 관련된 비즈니스 로직을 수행합니다.
 * ActionRule을 기반으로 요청의 대기열 적용 여부를 판단하는 로직이 추가되었습니다.
 */
@Service
@RequiredArgsConstructor
public class QueueApplicationService {

    // 의존성 주입: 각 도메인 서비스와 RuleMatcher를 사용합니다.
    private final ActionDomainService actionDomainService;
    private final QueueDomainService queueDomainService;
    private final TokenDomainService tokenDomainService;
    private final RuleMatcher ruleMatcher;

    /**
     * 사용자의 대기열 진입 요청을 처리하고, 현재 상태에 따라 적절한 응답을 반환합니다.
     * 이 메소드는 ActionRule을 검사하여 요청의 대기열 적용 여부를 동적으로 결정합니다.
     *
     * @param actionUrl     사용자가 접근하려는 액션의 URL (예: "/products/limited-edition")
     * @param customerId    고객을 식별하는 고유 ID
     * @param requestParams 사용자가 요청한 모든 쿼리 파라미터 맵 (ActionRule 검사에 사용)
     * @return Mono<CheckOrEnterResponse> 대기 상태 및 발급된 토큰 정보를 포함하는 응답
     */
    public Mono<CheckOrEnterResponse> checkOrEnterQueue(String actionUrl, String customerId, Map<String, String> requestParams) {
        // 1. actionUrl을 기반으로 Action 정보를 조회합니다.
        // Mono.flatMap을 사용하여 Action 객체가 조회된 후에 다음 로직을 수행합니다.
        // TODO 지금은 유지, 추후에 findById로 교체 확인
        return actionDomainService.findActionByUrl(actionUrl)
            .flatMap(action ->
                // 2. 조회된 Action에 연결된 모든 ActionRule들을 조회합니다. // TODO actionRules는 위에서 이미 가져오고 있음
                // Flux<ActionRule>을 Mono<List<ActionRule>>로 변환하여 모든 규칙을 한 번에 처리할 수 있도록 합니다.
                actionDomainService.findRulesByActionId(action.getId())
                    .collectList()
                    .flatMap(rules -> {
                        // 3. RuleMatcher를 사용하여 현재 요청이 대기열 적용 대상인지 판단합니다.
                        // Action의 기본 규칙(DefaultRuleType)과 ActionRule 목록, 실제 요청 파라미터를 종합적으로 고려합니다.
                        if (!ruleMatcher.isRequestSubjectToQueue(action, rules, requestParams)) {
                            // 3a. RuleMatcher가 대기열 적용 대상이 아니라고 판단한 경우 (예: 특정 규칙에 의해 우회됨).
                            // 즉시 입장 가능한 토큰을 발급하고, 상태를 "BYPASSED_BY_RULE"로 설정하여 응답합니다.
                            // 이는 대기열을 거치지 않고 바로 통과되었음을 명시적으로 나타냅니다.
                            return issueNewAllowedToken(customerId, action, "BYPASSED_BY_RULE");
                        }

                        // 4. RuleMatcher가 대기열 적용 대상이라고 판단한 경우, 기존의 대기열 로직을 수행합니다.
                        // 4a. 먼저 Action이 현재 효과적으로 활성화되어 있는지 확인합니다.
                        return actionDomainService.isActionEffectivelyEnabled(action)
                            .flatMap(isEnabled -> {
                                if (!isEnabled) {
                                    // 4a-1. Action이 비활성화된 경우, 대기열 진입 없이 "DISABLED" 상태를 반환합니다.
                                    return Mono.just(new CheckOrEnterResponse("DISABLED", null, null));
                                }

                                // 4b. 고객이 이미 유효한 토큰을 가지고 있는지 확인합니다.
                                // TODO token은 저장할 필요 x, JwtUtil.validateToken 으로 검증만 진행
                                return tokenDomainService.findValidTokenJwt(customerId, action.getId())
                                    .flatMap(existingJwt ->
                                        // 4b-1. 유효한 토큰이 있는 경우, 해당 토큰의 현재 대기 순번을 조회하여 반환합니다.
                                        // "EXISTING" 상태는 사용자가 이미 유효한 토큰을 가지고 있음을 나타냅니다.
                                        queueDomainService.getQueueRank(action.getId(), jwtToQueueId(existingJwt))
                                            .map(rank -> new CheckOrEnterResponse("EXISTING", existingJwt, rank)))
                                    .switchIfEmpty(Mono.defer(() ->
                                        // 4b-2. 유효한 토큰이 없는 경우, 대기 필요 여부를 판단합니다.
                                        queueDomainService.isWaitingRequired(action.getActionGroupId())
                                            .flatMap(isWaiting -> {
                                                if (isWaiting) {
                                                    // 4b-2-1. 대기가 필요한 경우, WAITING 상태의 토큰을 발급하고 대기열에 추가합니다.
                                                    return issueNewWaitingToken(customerId, action);
                                                } else {
                                                    // 4b-2-2. 즉시 입장 가능한 경우, ALLOWED 상태의 토큰을 발급합니다.
                                                    return issueNewAllowedToken(customerId, action, WaitStatus.READY.name());
                                                }
                                            })));
                            });
                    }))
            // 5. actionUrl에 해당하는 Action을 찾을 수 없는 경우, "ACTION_NOT_FOUND" 상태를 반환합니다.
            .switchIfEmpty(Mono.just(new CheckOrEnterResponse("ACTION_NOT_FOUND", null, null)));
    }

    /**
     * 새로운 WAITING 상태의 토큰을 발급하고, 사용자를 대기열에 추가합니다.
     *
     * @param customerId 고객 ID
     * @param action     관련 Action 정보
     * @return Mono<CheckOrEnterResponse> WAITING 상태 응답 (토큰 및 대기 순번 포함)
     */
    private Mono<CheckOrEnterResponse> issueNewWaitingToken(String customerId, Action action) {
        // 1. TokenDomainService를 통해 WAITING 상태의 새로운 토큰을 발급합니다.
        return tokenDomainService.issueToken(customerId, action, WaitStatus.WAITING.name())
            .flatMap(newJwt ->
                // 2. 발급된 토큰을 사용하여 사용자를 대기열에 추가하고, 대기 순번을 조회합니다.
                queueDomainService.addUserToQueue(action.getId(), jwtToQueueId(newJwt))
                    .map(rank ->
                        // 3. WAITING 상태, 발급된 토큰, 그리고 대기 순번을 포함하는 응답 객체를 생성하여 반환합니다.
                        new CheckOrEnterResponse(WaitStatus.WAITING.name(), newJwt, rank)));
    }

    /**
     * 새로운 ALLOWED 상태의 토큰을 발급합니다. (즉시 입장 가능)
     *
     * @param customerId 고객 ID
     * @param action     관련 Action 정보
     * @param status     응답에 포함될 상태 문자열 (WaitStatus.ALLOWED.name() 또는 "BYPASSED_BY_RULE")
     * @return Mono<CheckOrEnterResponse> ALLOWED 상태 응답 (토큰 포함, 대기 순번은 0L)
     */
    private Mono<CheckOrEnterResponse> issueNewAllowedToken(String customerId, Action action, String status) {
        // 1. TokenDomainService를 통해 ALLOWED 상태의 새로운 토큰을 발급합니다.
        return tokenDomainService.issueToken(customerId, action, WaitStatus.READY.name())
            .map(newJwt ->
                // 2. 지정된 상태(status), 발급된 토큰, 그리고 대기 순번 0L을 포함하는 응답 객체를 생성하여 반환합니다.
                // 0L은 대기 순번이 없음을 의미합니다.
                new CheckOrEnterResponse(status, newJwt, 0L));
    }

    /**
     * JWT 토큰에서 queueId (또는 고객 식별자)를 추출합니다.
     * 이 메소드는 JWT의 페이로드에서 실제 고객 식별자(예: subject)를 파싱하여 반환해야 합니다.
     * 현재는 임시 구현으로, 실제 JwtUtil을 사용하여 토큰 파싱 로직을 구현해야 합니다.
     *
     * @param jwt JWT 토큰 문자열
     * @return 추출된 queueId 문자열 (현재는 임시 값)
     */
    private String jwtToQueueId(String jwt) {
        // TODO: 실제 JwtUtil을 사용하여 subject 등을 추출하는 로직으로 대체해야 합니다.
        // 예: return jwtUtil.extractSubject(jwt);
        return "queueId-from-" + jwt;
    }
}