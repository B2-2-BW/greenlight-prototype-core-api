package com.winten.greenlight.prototype.core.api.controller.queue;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
@RestController
@RequestMapping("/queue")
public class QueueController {
    private final QueueService queueService;

    @PostMapping("/enter")
    public Mono<String> enterQueue(@RequestBody QueueRequest request) {
        return queueService.enterQueue(request.getActionId())
            .map(token -> {
                // 쿠키 설정은 FE에서 처리하거나, 별도 헤더로 반환
                return token;
            });
    }

    @PostMapping("/heartbeat")
    public Mono<Void> heartbeat(@CookieValue("queueToken") String token) {
        return queueService.heartbeat(token);
    }

    @Data
    static class QueueRequest {
        private String actionId;
    }
}
