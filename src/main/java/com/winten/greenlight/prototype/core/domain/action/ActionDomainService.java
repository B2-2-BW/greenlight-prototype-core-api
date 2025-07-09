package com.winten.greenlight.prototype.core.domain.action;

import com.winten.greenlight.prototype.core.db.repository.redis.action.ActionRepository;
import com.winten.greenlight.prototype.core.db.repository.redis.action.ActionRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Action 도메인의 비즈니스 로직을 담당하는 서비스입니다.
 * Action 및 ActionGroup 정보 조회, 활성화 여부 판단 등의 기능을 제공합니다.
 */
@Service
@RequiredArgsConstructor
public class ActionDomainService {
    private final ActionRepository actionRepository; // 기존 의존성
    private final ActionRuleRepository actionRuleRepository; // [NEW] 신규 의존성

    public Mono<Action> findActionByUrl(String url) {
        // 실제 구현은 DB 조회 로직
        return actionRepository.findByUrl(url);
    }

    public Mono<Boolean> isActionEffectivelyEnabled(Action action) {
        // 실제 구현은 Action의 상태 및 그룹 상태를 종합적으로 판단
        return Mono.just(true);
    }

    /**
     * [NEW] 특정 Action에 속한 모든 ActionRule을 조회하는 메소드
     */
    public Flux<ActionRule> findRulesByActionId(Long actionId) {
        return actionRuleRepository.findByActionId(actionId);
    }
}
