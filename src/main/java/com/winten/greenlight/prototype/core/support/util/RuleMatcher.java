package com.winten.greenlight.prototype.core.support.util;

import com.winten.greenlight.prototype.core.domain.action.Action;
import com.winten.greenlight.prototype.core.domain.action.ActionRule;
import com.winten.greenlight.prototype.core.domain.action.DefaultRuleType;
import com.winten.greenlight.prototype.core.domain.action.MatchOperator;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Rule Matcher (신규 구현)
 * - MatchOperator에 따라 실제 값 비교를 수행합니다.
 */
@Component
public class RuleMatcher {

    public boolean isRequestSubjectToQueue(Action action, List<ActionRule> rules, Map<String, String> requestParams) {
        DefaultRuleType defaultRule = action.getDefaultRuleType();

        // DefaultRuleType.ALL: 모든 요청에 대기열을 적용합니다. (규칙 무시)
        if (defaultRule == DefaultRuleType.ALL) {
            return true;
        }

        // DefaultRuleType.INCLUDE: 규칙 중 하나라도 일치할 경우에만 대기열을 적용합니다.
        if (defaultRule == DefaultRuleType.INCLUDE) {
            for (ActionRule rule : rules) {
                String requestValue = requestParams.get(rule.getParamName());
                if (requestValue != null && this.matches(requestValue, rule.getParamValue(), rule.getMatchOperator())) {
                    return true; // 규칙 일치 -> 대기열 적용
                }
            }
            return false; // 어떤 규칙에도 일치하지 않음 -> 대기열 미적용
        }

        // DefaultRuleType.EXCLUDE: 규칙 중 하나라도 일치할 경우 대기열을 적용하지 않습니다.
        if (defaultRule == DefaultRuleType.EXCLUDE) {
            for (ActionRule rule : rules) {
                String requestValue = requestParams.get(rule.getParamName());
                if (requestValue != null && this.matches(requestValue, rule.getParamValue(), rule.getMatchOperator())) {
                    return false; // 규칙 일치 -> 대기열 미적용
                }
            }
            return true; // 어떤 규칙에도 일치하지 않음 -> 대기열 적용
        }

        return true; // 안전을 위해, 정의되지 않은 정책은 모두 대기열 적용
    }

    private boolean matches(String requestValue, String ruleValue, MatchOperator operator) {
        if (requestValue == null || ruleValue == null || operator == null) {
            return false;
        }
        switch (operator) {
            case EQUAL:
                return requestValue.equals(ruleValue);
            case CONTAINS:
                return requestValue.contains(ruleValue);
            case STARTSWITH:
                return requestValue.startsWith(ruleValue);
            case ENDSWITH:
                return requestValue.endsWith(ruleValue);
            default:
                return false;
        }
    }
}

