package com.winten.greenlight.prototype.core.api.controller.queue;

import com.winten.greenlight.prototype.core.domain.customer.WaitStatus;
import com.winten.greenlight.prototype.core.domain.queue.QueueSseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/waiting")
@RequiredArgsConstructor
public class QueueSseController {
    private final QueueSseService queueSseService;

    // SSE 연동
    @GetMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<WaitStatus>> connectSse(
            @RequestParam Long actionGroupId,
            @RequestParam String entryId
    ) {
        return queueSseService.connect(actionGroupId, entryId)
                .map(status -> ServerSentEvent.builder(status).build());
    }


    // 고객 상태 조회
    // 고객이 어떤 queue에 들어가있는지 확인
    @GetMapping("/status")
    public Mono<WaitStatus> findUserQueueStatus(Long actionGroupId, String entryId) {
        return queueSseService.findUserStatus(actionGroupId, entryId);
    }
}