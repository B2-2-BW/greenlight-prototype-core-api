package com.winten.greenlight.prototype.core.api.controller.queue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueueControllerTest {

    @InjectMocks
    private QueueController queueController;

    @Mock
    private QueueService queueService;

    @Mock
    private ServerWebExchange exchange;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToController(queueController).build();
    }

    @Test
    void testEnterQueue() {
        // Given
        String actionId = "event123";
        String queueId = "queue123";
        String requestBody = "{\"actionId\":\"" + actionId + "\"}";

        when(queueService.enterQueue(eq(actionId))).thenReturn(Mono.just(queueId));

        // When
        webTestClient.post()
                .uri("/queue/enter")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .exchange()
                // Then
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo(queueId);
    }

    @Test
    void testHeartbeatWithValidToken() {
        // Given
        String token = "valid.token";
        String queueId = "queue123";

        when(queueService.heartbeat(eq(token))).thenReturn(Mono.empty());

        // When
        webTestClient.post()
                .uri("/queue/heartbeat")
                .cookie("queueToken", token)
                .exchange()
                // Then
                .expectStatus().isOk();
    }
}
