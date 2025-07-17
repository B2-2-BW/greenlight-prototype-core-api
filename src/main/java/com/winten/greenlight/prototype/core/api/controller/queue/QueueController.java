package com.winten.greenlight.prototype.core.api.controller.queue;

import com.winten.greenlight.prototype.core.domain.queue.QueueApplicationService;
import com.winten.greenlight.prototype.core.domain.customer.EntryTicket;
import com.winten.greenlight.prototype.core.support.error.CoreException;
import com.winten.greenlight.prototype.core.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

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
     * 대기열 상태 확인 API 엔드포인트입니다.
     * 사용자가 특정 액션(URL)에 접근할 때 호출되어, 대기열 적용 대상인지를 판단하여 상태를 반환합니다.
     *
     * @param actionId      사용자가 접근하려는 액션의 ID
     * @param greenlightToken (Optional) 고객이 보유한 대기열 토큰
     * @return Mono<EntryTicket> 대기 상태 및 토큰 정보
     */
    @GetMapping("/check-or-enter")
    public Mono<EntryTicket> checkOrEnterQueue(
            @RequestParam Long actionId,
            @RequestHeader(name = "X-GREENLIGHT-TOKEN", required = false) String greenlightToken,
            @RequestParam Map<String, String> requestParams
    ) {
        if (actionId == null) {
            return Mono.error(new CoreException(ErrorType.BAD_REQUEST, "actionId is required."));
        }

        return queueApplicationService.checkOrEnterQueue(actionId, greenlightToken, requestParams);
    }
}