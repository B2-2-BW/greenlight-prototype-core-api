package com.winten.greenlight.prototype.core.domain.queue;

import com.winten.greenlight.prototype.core.domain.action.ActionDomainService;
import com.winten.greenlight.prototype.core.domain.customer.WaitStatus;
import com.winten.greenlight.prototype.core.domain.customer.EntryTicket;
import com.winten.greenlight.prototype.core.support.error.CoreException;
import com.winten.greenlight.prototype.core.support.error.ErrorType;
import com.winten.greenlight.prototype.core.support.util.RuleMatcher;
import com.winten.greenlight.prototype.core.domain.token.TokenDomainService;
import io.hypersistence.tsid.TSID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 대기열 시스템의 핵심 애플리케이션 서비스입니다.
 * 클라이언트의 요청을 받아 여러 도메인 서비스(Action, Queue)를 조율하여
 * 대기열 적용 여부를 판단하고 상태를 반환합니다.
 */
@Service
@RequiredArgsConstructor
public class QueueApplicationService {

    private final ActionDomainService actionDomainService;
    private final QueueDomainService queueDomainService;
    private final RuleMatcher ruleMatcher;
    private final TokenDomainService tokenDomainService;

    /**
     * 사용자의 대기열 상태를 확인하고, 현재 상태에 따라 적절한 응답을 반환합니다.
     * 이 메소드는 ActionRule을 검사하여 요청의 대기열 적용 여부를 동적으로 결정합니다.
     *
     * @param actionId      사용자가 접근하려는 액션의 ID
     * @param greenlightToken (Optional) 고객이 보유한 대기열 토큰
     * @param requestParams 사용자가 요청한 모든 쿼리 파라미터 맵 (ActionRule 검사에 사용)
     * @return Mono<EntryTicket> 대기 상태 및 토큰 정보
     */
    public Mono<EntryTicket> checkOrEnterQueue(Long actionId, String greenlightToken, Map<String, String> requestParams) {
        return actionDomainService.findActionById(actionId)
            .switchIfEmpty(Mono.error(new CoreException(ErrorType.ACTION_NOT_FOUND, "Action not found for ID: " + actionId)))
            .flatMap(action -> {
                // 1. 큐 적용 대상이 아닌 경우, BYPASSED 처리
                if (!ruleMatcher.isRequestSubjectToQueue(action, action.getActionRules(), requestParams)) {
                    return Mono.just(new EntryTicket(WaitStatus.BYPASSED, null));
                }

                // 2. 액션이 비활성화된 경우, DISABLED 처리
                return actionDomainService.isActionEffectivelyEnabled(action)
                    .flatMap(isEnabled -> {
                        if (!isEnabled) {
                            return Mono.just(new EntryTicket(WaitStatus.DISABLED, null));
                        }

                        // 3. 토큰이 있는 경우, 이전 action의 토큰인지 여부 판단
                        if (StringUtils.hasText(greenlightToken)) {
                            return tokenDomainService.getActionIdFromToken(greenlightToken)
                                .flatMap(tokenActionId -> {
                                    if (actionId.equals(tokenActionId)) {
                                        // 1-2. 만약 이전 action의 토큰이 아니라면, READY 상태 반환
                                        return Mono.just(new EntryTicket(WaitStatus.READY, greenlightToken));
                                    } else {
                                        // 1-1. 만약 이전 action의 토큰이라면, waiting queue에 저장하는 로직 태운다.
                                        return handleNewEntry(actionId, action.getActionGroupId(), action, requestParams);
                                    }
                                })
                                .switchIfEmpty(handleNewEntry(actionId, action.getActionGroupId(), action, requestParams))
                                .onErrorResume(e -> {
                                    // 토큰에서 actionId 추출 실패 시 신규 진입자로 처리
                                    return handleNewEntry(actionId, action.getActionGroupId(), action, requestParams);
                                });
                        } else {
                            // 4. 토큰이 없는 신규 진입자 처리
                            return handleNewEntry(actionId, action.getActionGroupId(), action, requestParams);
                        }
                    });
            });
    }

    /**
     * 토큰이 없거나 유효하지 않은 경우, 혹은 다른 actionId의 토큰인 경우 신규 진입자를 처리합니다.
     * customerId를 생성하고 대기열 필요 여부에 따라 WAITING 또는 READY 큐에 추가합니다.
     *
     * @param actionId 액션 ID
     * @param actionGroupId 액션 그룹 ID
     * @param action Action 객체
     * @param requestParams 요청 파라미터 맵
     * @return Mono<EntryTicket> 대기 상태 및 토큰 정보
     */
    private Mono<EntryTicket> handleNewEntry(Long actionId, Long actionGroupId, com.winten.greenlight.prototype.core.domain.action.Action action, Map<String, String> requestParams) {
        return generateCustomerId(actionId)
            .flatMap(customerId -> 
                queueDomainService.isWaitingRequired(actionGroupId)
                    .flatMap(isWaiting -> {
                        if (isWaiting) {
                            // 5. 대기가 필요한 경우: WAITING 토큰 발급 및 대기열 등록
                            return tokenDomainService.issueToken(customerId, action, WaitStatus.WAITING.name())
                                .flatMap(newJwt ->
                                    queueDomainService.addUserToQueue(actionGroupId, customerId, WaitStatus.WAITING)
                                        .thenReturn(new EntryTicket(WaitStatus.WAITING, newJwt))
                                );
                        } else {
                            // 6. 대기가 필요 없는 경우: READY 토큰 발급 및 준비열 등록
                            return tokenDomainService.issueToken(customerId, action, WaitStatus.READY.name())
                                .flatMap(newJwt ->
                                    queueDomainService.addUserToQueue(actionGroupId, customerId, WaitStatus.READY)
                                        .thenReturn(new EntryTicket(WaitStatus.READY, newJwt))
                                );
                        }
                    })
            );
    }

    /**
     * actionId를 기반으로 고유한 customerId를 생성합니다.
     * customerId는 {actionId}:{tsid} 형식입니다.
     *
     * @param actionId 액션 ID
     * @return Mono<String> 생성된 customerId
     */
    private Mono<String> generateCustomerId(Long actionId) {
        return Mono.fromCallable(() -> actionId + ":" + TSID.fast().toString());
    }
}