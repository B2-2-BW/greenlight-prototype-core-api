package com.winten.greenlight.prototype.core.domain.action;

import com.winten.greenlight.prototype.core.db.repository.redis.action.ActionRepository;
import com.winten.greenlight.prototype.core.support.error.CoreException;
import com.winten.greenlight.prototype.core.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class CachedActionService {
    private final ActionRepository actionRepository;

    @Cacheable(cacheNames = "action", key = "#actionId")
    public Mono<Action> getActionById(final Long actionId) {
        return actionRepository.getActionById(actionId)
                .switchIfEmpty(Mono.error(CoreException.of(ErrorType.ACTION_NOT_FOUND, "Action을 찾을 수 없습니다. actionId: " + actionId)));
    }
    @Cacheable(cacheNames = "action-url", key = "#url")
    public Mono<Action> findActionByUrl(String url) {
        // 실제 구현은 DB 조회 로직
        return actionRepository.findByUrl(url);
    }
    @CacheEvict(cacheNames = "action", key = "#actionId")
    public Mono<Void> invalidateActionCache(Long actionId) {
        return Mono.empty();
    }

    @Cacheable(cacheNames = "actionGroup", key = "#actionGroupId")
    public Mono<ActionGroup> getActionGroupById(final Long actionGroupId) {
        return actionRepository.getActionGroupById(actionGroupId)
                .switchIfEmpty(Mono.error(CoreException.of(ErrorType.ACTION_GROUP_NOT_FOUND, "Action Group을 찾을 수 없습니다. actionGroupId: " + actionGroupId)));
    }

    @CacheEvict(cacheNames = "actionGroup", key = "#actionGroupId")
    public Mono<Void> invalidateActionGroupCache(Long actionGroupId) {
        return Mono.empty();
    }
}