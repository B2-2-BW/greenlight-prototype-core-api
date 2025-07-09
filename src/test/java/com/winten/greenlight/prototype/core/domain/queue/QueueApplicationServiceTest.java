
package com.winten.greenlight.prototype.core.domain.queue;

import com.winten.greenlight.prototype.core.api.controller.queue.dto.CheckOrEnterResponse;
import com.winten.greenlight.prototype.core.domain.action.Action;
import com.winten.greenlight.prototype.core.domain.action.ActionDomainService;
import com.winten.greenlight.prototype.core.domain.action.DefaultRuleType;
import com.winten.greenlight.prototype.core.domain.customer.WaitStatus;
import com.winten.greenlight.prototype.core.domain.token.TokenDomainService;
import com.winten.greenlight.prototype.core.support.util.RuleMatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@TestPropertySource(properties = { "LOKI_URL=http://localhost:3100/loki/api/v1/push" })
@SpringBootTest(classes = {QueueApplicationService.class, QueueApplicationServiceTest.TestConfig.class})
@DisplayName("QueueApplicationService 통합 테스트 (DefaultRuleType 반영)")
class QueueApplicationServiceTest {

    @Autowired
    private QueueApplicationService queueApplicationService;

    @Autowired
    private ActionDomainService actionDomainService;
    @Autowired
    private QueueDomainService queueDomainService;
    @Autowired
    private TokenDomainService tokenDomainService;
    @Autowired
    private RuleMatcher ruleMatcher;

    @TestConfiguration
    static class TestConfig {
        @Bean
        public ActionDomainService actionDomainService() {
            return Mockito.mock(ActionDomainService.class);
        }

        @Bean
        public QueueDomainService queueDomainService() {
            return Mockito.mock(QueueDomainService.class);
        }

        @Bean
        public TokenDomainService tokenDomainService() {
            return Mockito.mock(TokenDomainService.class);
        }

        @Bean
        public RuleMatcher ruleMatcher() {
            return Mockito.mock(RuleMatcher.class);
        }
    }

    private static final String TEST_ACTION_URL = "/products/limited-edition";
    private static final String TEST_CUSTOMER_ID = "customer-123";
    private static final Long TEST_ACTION_ID = 1L;
    private static final Long TEST_ACTION_GROUP_ID = 10L;

    private Action testAction;
    private Map<String, String> defaultRequestParams;

    @BeforeEach
    void setUp() {
        testAction = new Action();
        testAction.setId(TEST_ACTION_ID);
        testAction.setActionGroupId(TEST_ACTION_GROUP_ID);
        testAction.setActionUrl(TEST_ACTION_URL);

        defaultRequestParams = Map.of("actionUrl", TEST_ACTION_URL, "customerId", TEST_CUSTOMER_ID);

        when(actionDomainService.findActionByUrl(TEST_ACTION_URL)).thenReturn(Mono.just(testAction));
        when(actionDomainService.isActionEffectivelyEnabled(testAction)).thenReturn(Mono.just(true));
        when(actionDomainService.findRulesByActionId(TEST_ACTION_ID)).thenReturn(Flux.empty()); // 기본적으로 규칙 없음
    }

    // --- DefaultRuleType.ALL 시나리오 --- //

    @Test
    @DisplayName("DefaultRuleType.ALL: 규칙이 없거나 매칭되지 않아도 항상 대기열을 적용한다")
    void checkOrEnterQueue_DefaultRuleTypeAll_AlwaysApplyQueue() {
        // given
        testAction.setDefaultRuleType(DefaultRuleType.ALL); // 기본 정책: ALL
        // RuleMatcher는 항상 true를 반환하도록 Mocking (실제 로직은 ALL일 때 규칙 무시)
        when(ruleMatcher.isRequestSubjectToQueue(any(Action.class), anyList(), anyMap())).thenReturn(true);
        when(tokenDomainService.findValidTokenJwt(TEST_CUSTOMER_ID, TEST_ACTION_ID)).thenReturn(Mono.empty());
        when(queueDomainService.isWaitingRequired(TEST_ACTION_GROUP_ID)).thenReturn(Mono.just(false)); // 대기 불필요 시나리오
        when(tokenDomainService.issueToken(TEST_CUSTOMER_ID, testAction, WaitStatus.READY.name()))
            .thenReturn(Mono.just("all-allowed-token"));

        // when
        Mono<CheckOrEnterResponse> result = queueApplicationService.checkOrEnterQueue(TEST_ACTION_URL, TEST_CUSTOMER_ID, defaultRequestParams);

        // then
        StepVerifier.create(result)
            .expectNextMatches(response ->
                response.getStatus().equals(WaitStatus.READY.name()) &&
                    response.getToken().equals("all-allowed-token")
            )
            .verifyComplete();
    }

    // --- DefaultRuleType.INCLUDE 시나리오 --- //

    @Test
    @DisplayName("DefaultRuleType.INCLUDE: 규칙에 일치하여 대기열을 적용한다")
    void checkOrEnterQueue_DefaultRuleTypeInclude_MatchRule_ApplyQueue() {
        // given
        testAction.setDefaultRuleType(DefaultRuleType.INCLUDE); // 기본 정책: INCLUDE
        // RuleMatcher가 규칙에 일치하여 대기열 적용을 지시
        when(ruleMatcher.isRequestSubjectToQueue(any(Action.class), anyList(), anyMap())).thenReturn(true);
        when(tokenDomainService.findValidTokenJwt(TEST_CUSTOMER_ID, TEST_ACTION_ID)).thenReturn(Mono.empty());
        when(queueDomainService.isWaitingRequired(TEST_ACTION_GROUP_ID)).thenReturn(Mono.just(true)); // 대기 필요 시나리오
        when(tokenDomainService.issueToken(TEST_CUSTOMER_ID, testAction, WaitStatus.WAITING.name()))
            .thenReturn(Mono.just("include-waiting-token"));
        when(queueDomainService.addUserToQueue(eq(TEST_ACTION_ID), anyString())).thenReturn(Mono.just(25L));

        // when
        Mono<CheckOrEnterResponse> result = queueApplicationService.checkOrEnterQueue(TEST_ACTION_URL, TEST_CUSTOMER_ID, defaultRequestParams);

        // then
        StepVerifier.create(result)
            .expectNextMatches(response ->
                response.getStatus().equals(WaitStatus.WAITING.name()) &&
                    response.getToken().equals("include-waiting-token") &&
                    response.getRank() == 25L
            )
            .verifyComplete();
    }

    @Test
    @DisplayName("DefaultRuleType.INCLUDE: 규칙에 일치하지 않아 대기열을 적용하지 않는다")
    void checkOrEnterQueue_DefaultRuleTypeInclude_NoMatchRule_NoQueue() {
        // given
        testAction.setDefaultRuleType(DefaultRuleType.INCLUDE); // 기본 정책: INCLUDE
        // RuleMatcher가 규칙에 일치하지 않아 대기열 미적용을 지시
        when(ruleMatcher.isRequestSubjectToQueue(any(Action.class), anyList(), anyMap())).thenReturn(false);
        when(tokenDomainService.issueToken(TEST_CUSTOMER_ID, testAction, WaitStatus.READY.name()))
            .thenReturn(Mono.just("include-bypassed-token"));

        // when
        Mono<CheckOrEnterResponse> result = queueApplicationService.checkOrEnterQueue(TEST_ACTION_URL, TEST_CUSTOMER_ID, defaultRequestParams);

        // then
        StepVerifier.create(result)
            .expectNextMatches(response ->
                response.getStatus().equals("BYPASSED_BY_RULE") &&
                    response.getToken().equals("include-bypassed-token")
            )
            .verifyComplete();
    }

    // --- DefaultRuleType.EXCLUDE 시나리오 --- //

    @Test
    @DisplayName("DefaultRuleType.EXCLUDE: 규칙에 일치하여 대기열을 적용하지 않는다")
    void checkOrEnterQueue_DefaultRuleTypeExclude_MatchRule_NoQueue() {
        // given
        testAction.setDefaultRuleType(DefaultRuleType.EXCLUDE); // 기본 정책: EXCLUDE
        // RuleMatcher가 규칙에 일치하여 대기열 미적용을 지시
        when(ruleMatcher.isRequestSubjectToQueue(any(Action.class), anyList(), anyMap())).thenReturn(false);
        when(tokenDomainService.issueToken(TEST_CUSTOMER_ID, testAction, WaitStatus.READY.name()))
            .thenReturn(Mono.just("exclude-bypassed-token"));

        // when
        Mono<CheckOrEnterResponse> result = queueApplicationService.checkOrEnterQueue(TEST_ACTION_URL, TEST_CUSTOMER_ID, defaultRequestParams);

        // then
        StepVerifier.create(result)
            .expectNextMatches(response ->
                response.getStatus().equals("BYPASSED_BY_RULE") &&
                    response.getToken().equals("exclude-bypassed-token")
            )
            .verifyComplete();
    }

    @Test
    @DisplayName("DefaultRuleType.EXCLUDE: 규칙에 일치하지 않아 대기열을 적용한다")
    void checkOrEnterQueue_DefaultRuleTypeExclude_NoMatchRule_ApplyQueue() {
        // given
        testAction.setDefaultRuleType(DefaultRuleType.EXCLUDE); // 기본 정책: EXCLUDE
        // RuleMatcher가 규칙에 일치하지 않아 대기열 적용을 지시
        when(ruleMatcher.isRequestSubjectToQueue(any(Action.class), anyList(), anyMap())).thenReturn(true);
        when(tokenDomainService.findValidTokenJwt(TEST_CUSTOMER_ID, TEST_ACTION_ID)).thenReturn(Mono.empty());
        when(queueDomainService.isWaitingRequired(TEST_ACTION_GROUP_ID)).thenReturn(Mono.just(true)); // 대기 필요 시나리오
        when(tokenDomainService.issueToken(TEST_CUSTOMER_ID, testAction, WaitStatus.WAITING.name()))
            .thenReturn(Mono.just("exclude-waiting-token"));
        when(queueDomainService.addUserToQueue(eq(TEST_ACTION_ID), anyString())).thenReturn(Mono.just(30L));

        // when
        Mono<CheckOrEnterResponse> result = queueApplicationService.checkOrEnterQueue(TEST_ACTION_URL, TEST_CUSTOMER_ID, defaultRequestParams);

        // then
        StepVerifier.create(result)
            .expectNextMatches(response ->
                response.getStatus().equals(WaitStatus.WAITING.name()) &&
                    response.getToken().equals("exclude-waiting-token") &&
                    response.getRank() == 30L
            )
            .verifyComplete();
    }

    // --- 기존 시나리오 (DefaultRuleType.ALL로 가정하고 RuleMatcher가 true 반환) --- //

    @Test
    @DisplayName("기존 토큰 보유 시, EXISTING 상태와 기존 토큰, 현재 순번을 반환한다")
    void checkOrEnterQueue_ExistingUser() {
        // given
        testAction.setDefaultRuleType(DefaultRuleType.ALL); // ALL로 가정
        when(ruleMatcher.isRequestSubjectToQueue(any(Action.class), anyList(), anyMap())).thenReturn(true);
        String existingToken = "existing-token";
        when(tokenDomainService.findValidTokenJwt(TEST_CUSTOMER_ID, TEST_ACTION_ID)).thenReturn(Mono.just(existingToken));
        when(queueDomainService.getQueueRank(eq(TEST_ACTION_ID), anyString())).thenReturn(Mono.just(5L));

        // when
        Mono<CheckOrEnterResponse> result = queueApplicationService.checkOrEnterQueue(TEST_ACTION_URL, TEST_CUSTOMER_ID, defaultRequestParams);

        // then
        StepVerifier.create(result)
            .expectNextMatches(response ->
                response.getStatus().equals("EXISTING") &&
                    response.getToken().equals(existingToken) &&
                    response.getRank() == 5L
            )
            .verifyComplete();
    }

    @Test
    @DisplayName("Action이 비활성화된 경우, DISABLED 상태를 반환한다")
    void checkOrEnterQueue_ActionDisabled() {
        // given
        testAction.setDefaultRuleType(DefaultRuleType.ALL); // ALL로 가정
        when(ruleMatcher.isRequestSubjectToQueue(any(Action.class), anyList(), anyMap())).thenReturn(true);
        when(actionDomainService.isActionEffectivelyEnabled(testAction)).thenReturn(Mono.just(false));

        // when
        Mono<CheckOrEnterResponse> result = queueApplicationService.checkOrEnterQueue(TEST_ACTION_URL, TEST_CUSTOMER_ID, defaultRequestParams);

        // then
        StepVerifier.create(result)
            .expectNextMatches(response -> response.getStatus().equals("DISABLED"))
            .verifyComplete();
    }

    @Test
    @DisplayName("Action을 찾을 수 없는 경우, ACTION_NOT_FOUND 상태를 반환한다")
    void checkOrEnterQueue_ActionNotFound() {
        // given
        when(actionDomainService.findActionByUrl(TEST_ACTION_URL)).thenReturn(Mono.empty());

        // when
        Mono<CheckOrEnterResponse> result = queueApplicationService.checkOrEnterQueue(TEST_ACTION_URL, TEST_CUSTOMER_ID, defaultRequestParams);

        // then
        StepVerifier.create(result)
            .expectNextMatches(response -> response.getStatus().equals("ACTION_NOT_FOUND"))
            .verifyComplete();
    }
}