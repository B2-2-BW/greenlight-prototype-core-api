package com.winten.greenlight.prototype.core.api.controller.queue;

import com.winten.greenlight.prototype.core.api.controller.customer.CustomerEntryRequest;
import com.winten.greenlight.prototype.core.api.controller.queue.dto.CheckOrEnterResponse;
import com.winten.greenlight.prototype.core.domain.customer.WaitStatus;
import com.winten.greenlight.prototype.core.domain.queue.QueueApplicationService;
import com.winten.greenlight.prototype.core.support.error.CoreException;
import com.winten.greenlight.prototype.core.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import org.springframework.http.server.reactive.ServerHttpRequest;
import java.util.Map;


/**
 * 외부 HTTP 요청을 받아 Application Service로 전달하고, 결과를 응답(Response)합니다.
 * */
@RestController
@RequestMapping("/api/v1/queue")
@RequiredArgsConstructor
public class QueueController {

    private final QueueApplicationService queueApplicationService;

    /**
     * 대기열 진입 및 상태 확인 API 엔드포인트입니다.
     * 사용자가 특정 액션(URL)에 접근할 때 호출되어, 대기열 진입 여부를 판단하고 토큰을 발급합니다.
     *
     * @param actionUrl  사용자가 접근하려는 액션의 URL (예: "/products/limited-edition")
     * @param customerId 고객을 식별하는 고유 ID
     * @return Mono<CheckOrEnterResponse> 대기 상태 및 발급된 토큰 정보를 포함하는 응답
     */
    @GetMapping("/check-or-enter")
    public Mono<CheckOrEnterResponse> checkOrEnterQueue(
            @RequestBody CustomerEntryRequest request
    ) {
        // ServerHttpRequest에서 모든 쿼리 파라미터를 추출합니다.
        Map<String, String> requestParams = request.getQueryParams().toSingleValueMap();

        // 필수 파라미터인 actionUrl과 customerId를 추출합니다.
        String actionUrl = requestParams.get("actionUrl");
        // TODO customerId 안받음
        String customerId = requestParams.get("customerId");

        // TODO
        //  0. 현재 timestamp를 추출합니다.
        //  1. actionId를 기반으로 Action 정보를 조회합니다.
        //      Mono.flatMap을 사용하여 Action 객체가 조회된 후에 다음 로직을 수행합니다.
        //  2. RuleMatcher를 사용하여 현재 요청이 대기열 적용 대상인지 판단합니다.
        //      Action의 기본 규칙(DefaultRuleType)과 ActionRule 목록, 실제 요청 파라미터를 종합적으로 고려합니다.
        //      2a. RuleMatcher가 대기열 적용 대상이 아니라고 판단한 경우 (예: 특정 규칙에 의해 우회됨).
        //          즉시 입장 가능한, 사용자를 READY Queue에 넣고 입장 가능 반환
        //  3. RuleMatcher가 대기열 적용 대상이라고 판단한 경우, 기존의 대기열 로직을 수행합니다.
        //      3a. 먼저 Action Group이 현재 효과적으로 활성화되어 있는지 확인합니다.
        //          3a-1. Action Group이 비활성화된 경우, 대기열 진입 없이 바로 입장 가능한 응답 반환
        //  4b. 즉시 입장 가능여부 확인
        //      4b-1. 대기가 필요한 경우, WAITING 대기열에 추가하고 토큰 반환
        //      4b-2. 즉시 입장 가능한 경우, READY 대기열에 추가하고 토큰 반환.
        //  5. actionUrl에 해당하는 Action을 찾을 수 없는 경우, "ACTION_NOT_FOUND" 상태를 반환합니다.

        // TODO: actionUrl 또는 customerId가 null일 경우에 대한 예외 처리 필요
        if (actionUrl == null || customerId == null) {
            return Mono.just(new CheckOrEnterResponse("BAD_REQUEST", null, null));
            // TODO Mono.error 변경 및 CoreException 사용
//             return Mono.error(new CoreException(ErrorType.BAD_REQUEST, "에러 상세"));
        }

        Long timestamp = System.currentTimeMillis();
        return queueApplicationService.checkOrEnterQueue(actionUrl, customerId, requestParams);
    }
}