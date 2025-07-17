package com.winten.greenlight.prototype.core.support.util;

import com.winten.greenlight.prototype.core.domain.customer.WaitStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisKeyBuilder {
    @Value("${redis.key-prefix}")
    private String prefix;

    public String actionGroupMeta(Long actionGroupId) {
        return String.format("%s:action_group:%d:meta", prefix, actionGroupId);
    }

    public String actionGroupStatus(Long actionGroupId) {
        return String.format("%s:action_group:%d:status", prefix, actionGroupId);
    }

    public String action(Long actionId) {
        return String.format("%s:action:%d", prefix, actionId);
    }

    public String accessLog(Long actionGroupId) {
        return String.format("%s:action_group:%d:accesslog", prefix, actionGroupId);
    }
    public String queue(Long actionGroupId, WaitStatus waitStatus) {
        return String.format("%s:action_group:%d:queue:%s", prefix, actionGroupId, waitStatus);
    }
    // 활성 사용자 수를 저장하는 ZSET의 키
    public String activeUsers(Long actionGroupId) {
        return String.format("%s:action_group:%d:active_users", prefix, actionGroupId);
    }
    // 토큰 메타데이터를 저장하는 Hash의 키
    public String token(String jwt) {
        return String.format("%s:token:%s", prefix, jwt);
    }
    // customerId와 actionId로 JWT를 찾는 인덱스 키 (String)
    public String customerActionTokenIndex(String customerId, Long actionId) {
        return String.format("%s:customer:%s:action:%d:jwt", prefix, customerId, actionId);
    }
    // 대기열 키 (기존 queue 메서드와 유사하지만, actionId를 직접 받도록)
    // 기존 queue(Long actionGroupId, WaitStatus waitStatus)와는 다름
    public String waitingQueue(Long actionGroupId) {
        return String.format("%s:action_group:%d:queue:%s", prefix, actionGroupId, WaitStatus.WAITING);
    }
}