package com.winten.greenlight.prototype.core.domain.queue;

import com.winten.greenlight.prototype.core.domain.action.Action;
import com.winten.greenlight.prototype.core.domain.action.ActionDomainService;
import com.winten.greenlight.prototype.core.domain.action.ActionRule;
import com.winten.greenlight.prototype.core.domain.customer.EntryTicket;
import com.winten.greenlight.prototype.core.domain.customer.WaitStatus;
import com.winten.greenlight.prototype.core.domain.token.TokenDomainService;
import com.winten.greenlight.prototype.core.support.error.CoreException;
import com.winten.greenlight.prototype.core.support.error.ErrorType;
import com.winten.greenlight.prototype.core.support.util.RuleMatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueueApplicationServiceTest {

    @Mock
    private ActionDomainService actionDomainService;
    @Mock
    private QueueDomainService queueDomainService;
    @Mock
    private RuleMatcher ruleMatcher;
    @Mock
    private TokenDomainService tokenDomainService;

    @InjectMocks
    private QueueApplicationService queueApplicationService;

    private Long testActionId = 1L;
    private Long testActionGroupId = 100L;
    private String testGreenlightToken = "test_jwt_token";
    private Map<String, String> testRequestParams = new HashMap<>();
    private Action testAction;
    private List<ActionRule> testRules = Collections.emptyList();

    @BeforeEach
    void setUp() {
        testAction = Action.builder()
                .id(testActionId)
                .actionGroupId(testActionGroupId)
                .actionRules(testRules)
                .build();

        testRequestParams.put("param1", "value1");
    }

    @Test
    @DisplayName("액션을 찾을 수 없는 경우 CoreException.ACTION_NOT_FOUND 반환")
    void checkOrEnterQueue_actionNotFound_returnsError() {
        when(actionDomainService.findActionById(anyLong())).thenReturn(Mono.empty());

        StepVerifier.create(queueApplicationService.checkOrEnterQueue(testActionId, null, testRequestParams))
                .expectErrorMatches(throwable -> throwable instanceof CoreException &&
                        ((CoreException) throwable).getErrorType() == ErrorType.ACTION_NOT_FOUND)
                .verify();
    }

    @Test
    @DisplayName("RuleMatcher에 의해 BYPASSED 되는 경우 BYPASSED 상태 반환")
    void checkOrEnterQueue_bypassedByRule_returnsBypassed() {
        when(actionDomainService.findActionById(anyLong())).thenReturn(Mono.just(testAction));
        when(ruleMatcher.isRequestSubjectToQueue(any(Action.class), anyList(), anyMap())).thenReturn(false);

        StepVerifier.create(queueApplicationService.checkOrEnterQueue(testActionId, null, testRequestParams))
                .expectNextMatches(entryTicket -> entryTicket.getWaitStatus() == WaitStatus.BYPASSED)
                .verifyComplete();
    }

    @Test
    @DisplayName("액션이 비활성화된 경우 DISABLED 상태 반환")
    void checkOrEnterQueue_actionDisabled_returnsDisabled() {
        when(actionDomainService.findActionById(anyLong())).thenReturn(Mono.just(testAction));
        when(ruleMatcher.isRequestSubjectToQueue(any(Action.class), anyList(), anyMap())).thenReturn(true);
        when(actionDomainService.isActionEffectivelyEnabled(any(Action.class))).thenReturn(Mono.just(false));

        StepVerifier.create(queueApplicationService.checkOrEnterQueue(testActionId, null, testRequestParams))
                .expectNextMatches(entryTicket -> entryTicket.getWaitStatus() == WaitStatus.DISABLED)
                .verifyComplete();
    }

    @Test
    @DisplayName("유효한 토큰이 있고 actionId가 일치하는 경우 READY 상태 반환")
    void checkOrEnterQueue_validTokenMatchingActionId_returnsReady() {
        when(actionDomainService.findActionById(anyLong())).thenReturn(Mono.just(testAction));
        when(ruleMatcher.isRequestSubjectToQueue(any(Action.class), anyList(), anyMap())).thenReturn(true);
        when(actionDomainService.isActionEffectivelyEnabled(any(Action.class))).thenReturn(Mono.just(true));
        when(tokenDomainService.getActionIdFromToken(anyString())).thenReturn(Mono.just(testActionId));

        StepVerifier.create(queueApplicationService.checkOrEnterQueue(testActionId, testGreenlightToken, testRequestParams))
                .expectNextMatches(entryTicket ->
                        entryTicket.getWaitStatus() == WaitStatus.READY &&
                        entryTicket.getJwt() != null && entryTicket.getJwt().equals(testGreenlightToken))
                .verifyComplete();
    }

    @Test
    @DisplayName("유효한 토큰이 있지만 actionId가 일치하지 않는 경우 WAITING 큐로 이동")
    void checkOrEnterQueue_validTokenMismatchingActionId_movesToWaitingQueue() {
        Long otherActionId = 2L;
        when(actionDomainService.findActionById(anyLong())).thenReturn(Mono.just(testAction));
        when(ruleMatcher.isRequestSubjectToQueue(any(Action.class), anyList(), anyMap())).thenReturn(true);
        when(actionDomainService.isActionEffectivelyEnabled(any(Action.class))).thenReturn(Mono.just(true));
        when(tokenDomainService.getActionIdFromToken(anyString())).thenReturn(Mono.just(otherActionId)); // Mismatching actionId
        when(queueDomainService.isWaitingRequired(anyLong())).thenReturn(Mono.just(true)); // Waiting required
        when(tokenDomainService.issueToken(anyString(), any(Action.class), eq(WaitStatus.WAITING.name()))).thenReturn(Mono.just("new_jwt_token"));
        when(queueDomainService.addUserToQueue(anyLong(), anyString(), eq(WaitStatus.WAITING))).thenReturn(Mono.just(0L));

        StepVerifier.create(queueApplicationService.checkOrEnterQueue(testActionId, testGreenlightToken, testRequestParams))
                .expectNextMatches(entryTicket -> entryTicket.getWaitStatus() == WaitStatus.WAITING)
                .verifyComplete();
    }

    @Test
    @DisplayName("토큰에서 actionId를 추출할 수 없는 경우 WAITING 큐로 이동")
    void checkOrEnterQueue_cannotExtractActionIdFromToken_movesToWaitingQueue() {
        when(actionDomainService.findActionById(anyLong())).thenReturn(Mono.just(testAction));
        when(ruleMatcher.isRequestSubjectToQueue(any(Action.class), anyList(), anyMap())).thenReturn(true);
        when(actionDomainService.isActionEffectivelyEnabled(any(Action.class))).thenReturn(Mono.just(true));
        when(tokenDomainService.getActionIdFromToken(anyString())).thenReturn(Mono.empty()); // Cannot extract actionId
        when(queueDomainService.isWaitingRequired(anyLong())).thenReturn(Mono.just(true)); // Waiting required
        when(tokenDomainService.issueToken(anyString(), any(Action.class), eq(WaitStatus.WAITING.name()))).thenReturn(Mono.just("new_jwt_token"));
        when(queueDomainService.addUserToQueue(anyLong(), anyString(), eq(WaitStatus.WAITING))).thenReturn(Mono.just(0L));

        StepVerifier.create(queueApplicationService.checkOrEnterQueue(testActionId, testGreenlightToken, testRequestParams))
                .expectNextMatches(entryTicket -> entryTicket.getWaitStatus() == WaitStatus.WAITING)
                .verifyComplete();
    }

    @Test
    @DisplayName("토큰이 없는 경우 대기가 필요하면 WAITING 상태 반환")
    void checkOrEnterQueue_noTokenWaitingRequired_returnsWaiting() {
        when(actionDomainService.findActionById(anyLong())).thenReturn(Mono.just(testAction));
        when(ruleMatcher.isRequestSubjectToQueue(any(Action.class), anyList(), anyMap())).thenReturn(true);
        when(actionDomainService.isActionEffectivelyEnabled(any(Action.class))).thenReturn(Mono.just(true));
        when(queueDomainService.isWaitingRequired(anyLong())).thenReturn(Mono.just(true));
        when(tokenDomainService.issueToken(anyString(), any(Action.class), eq(WaitStatus.WAITING.name()))).thenReturn(Mono.just("new_jwt_token"));
        when(queueDomainService.addUserToQueue(anyLong(), anyString(), eq(WaitStatus.WAITING))).thenReturn(Mono.just(0L));

        StepVerifier.create(queueApplicationService.checkOrEnterQueue(testActionId, null, testRequestParams))
                .expectNextMatches(entryTicket -> entryTicket.getWaitStatus() == WaitStatus.WAITING)
                .verifyComplete();
    }

    @Test
    @DisplayName("토큰이 없는 경우 대기가 필요 없으면 READY 상태 반환")
    void checkOrEnterQueue_noTokenWaitingNotRequired_returnsReady() {
        when(actionDomainService.findActionById(anyLong())).thenReturn(Mono.just(testAction));
        when(ruleMatcher.isRequestSubjectToQueue(any(Action.class), anyList(), anyMap())).thenReturn(true);
        when(actionDomainService.isActionEffectivelyEnabled(any(Action.class))).thenReturn(Mono.just(true));
        when(queueDomainService.isWaitingRequired(anyLong())).thenReturn(Mono.just(false));
        when(tokenDomainService.issueToken(anyString(), any(Action.class), eq(WaitStatus.READY.name()))).thenReturn(Mono.just("new_jwt_token"));
        when(queueDomainService.addUserToQueue(anyLong(), anyString(), eq(WaitStatus.READY))).thenReturn(Mono.just(0L));

        StepVerifier.create(queueApplicationService.checkOrEnterQueue(testActionId, null, testRequestParams))
                .expectNextMatches(entryTicket -> entryTicket.getWaitStatus() == WaitStatus.READY)
                .verifyComplete();
    }
}