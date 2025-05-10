package com.winten.greenlight.prototype.core.config;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.winten.greenlight.prototype.core.support.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import org.springframework.http.HttpCookie;
import org.springframework.http.server.RequestPath;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class QueueWebFilterTest {

    @InjectMocks
    private QueueWebFilter queueWebFilter;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Mock
    private ServerWebExchange exchange;

    @Mock
    private WebFilterChain chain;

    @Mock
    private ServerHttpRequest request;

    @Mock
    private ServerHttpResponse response;

    @Mock
    private DataBufferFactory bufferFactory;

    @Mock
    private DecodedJWT decodedJWT;

    @Mock
    private HttpCookie queueTokenCookie;

    @Mock
    private RequestPath requestPath;

    @Mock
    private MultiValueMap<String, HttpCookie> cookies;

    @BeforeEach
    void setUp() {
        lenient().when(exchange.getRequest()).thenReturn(request);
        lenient().when(exchange.getResponse()).thenReturn(response);
        lenient().when(request.getPath()).thenReturn(requestPath);
        lenient().when(request.getCookies()).thenReturn(cookies);
        lenient().when(response.bufferFactory()).thenReturn(bufferFactory);
        Map<String, Object> attributes = new HashMap<>();
        lenient().when(exchange.getAttributes()).thenReturn(attributes);
    }

    @Test
    void testHeartbeatWithValidToken() {
        // Given
        String token = "valid.token";
        String queueId = "queue123";
        when(requestPath.toString()).thenReturn("/queue/heartbeat");
        when(cookies.getFirst("queueToken")).thenReturn(queueTokenCookie);
        when(queueTokenCookie.getValue()).thenReturn(token);
        when(jwtUtil.validateToken(token)).thenReturn(decodedJWT);
        when(decodedJWT.getSubject()).thenReturn(queueId);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        // 디버깅
        System.out.println("URI: /queue/heartbeat, Token: " + token);
        System.out.println("URI contains heartbeat: " + request.getPath().toString().contains("/heartbeat"));
        exchange.getAttributes().put("testKey", "testValue");
        assertEquals("testValue", exchange.getAttributes().get("testKey"));

        // When
        Mono<Void> result = queueWebFilter.filter(exchange, chain);

        // Then
        result.block();
        System.out.println("Attributes: " + exchange.getAttributes());
        assertEquals(queueId, exchange.getAttributes().get("queueId"));
        verify(jwtUtil, times(1)).validateToken(token);
        verify(chain).filter(exchange);
        verify(response, never()).setStatusCode(any());
    }

    @Test
    void testHeartbeatWithInvalidToken() {
        // Given
        String token = "invalid.token";
        when(requestPath.toString()).thenReturn("/queue/heartbeat");
        when(cookies.getFirst("queueToken")).thenReturn(queueTokenCookie);
        when(queueTokenCookie.getValue()).thenReturn(token);
        when(jwtUtil.validateToken(token)).thenThrow(new RuntimeException("Invalid or expired token"));
        when(bufferFactory.wrap(any(byte[].class))).thenReturn(mock(org.springframework.core.io.buffer.DataBuffer.class));
        when(response.writeWith(any(Mono.class))).thenReturn(Mono.empty());

        // When
        Mono<Void> result = queueWebFilter.filter(exchange, chain);

        // Then
        result.block();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        verify(response).writeWith(any(Mono.class));
        verify(chain, never()).filter(exchange);
        verify(bufferFactory).wrap("Invalid or expired token".getBytes());
    }

    @Test
    void testHeartbeatWithoutToken() {
        // Given
        when(requestPath.toString()).thenReturn("/queue/heartbeat");
        when(cookies.getFirst("queueToken")).thenReturn(null);
        when(bufferFactory.wrap(any(byte[].class))).thenReturn(mock(org.springframework.core.io.buffer.DataBuffer.class));
        when(response.writeWith(any(Mono.class))).thenReturn(Mono.empty());

        // When
        Mono<Void> result = queueWebFilter.filter(exchange, chain);

        // Then
        result.block();
        verify(response).setStatusCode(HttpStatus.BAD_REQUEST);
        verify(response).writeWith(any(Mono.class));
        verify(chain, never()).filter(exchange);
        verify(bufferFactory).wrap("Missing queueToken cookie".getBytes());
    }

    @Test
    void testActivityWithValidToken() {
        // Given
        String token = "valid.token";
        String queueId = "queue123";
        when(requestPath.toString()).thenReturn("/queue/activity");
        when(cookies.getFirst("queueToken")).thenReturn(queueTokenCookie);
        when(queueTokenCookie.getValue()).thenReturn(token);
        when(jwtUtil.validateToken(token)).thenReturn(decodedJWT);
        when(decodedJWT.getSubject()).thenReturn(queueId);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        // 디버깅
        System.out.println("URI: /queue/activity, Token: " + token);
        System.out.println("URI contains activity: " + request.getPath().toString().contains("/activity"));
        exchange.getAttributes().put("testKey", "testValue");
        assertEquals("testValue", exchange.getAttributes().get("testKey"));

        // When
        Mono<Void> result = queueWebFilter.filter(exchange, chain);

        // Then
        result.block();
        System.out.println("Attributes: " + exchange.getAttributes());
        assertEquals(queueId, exchange.getAttributes().get("queueId"));
        verify(jwtUtil, times(1)).validateToken(token);
        verify(chain).filter(exchange);
        verify(response, never()).setStatusCode(any());
    }

    @Test
    void testNonFilteredUri() {
        // Given
        when(requestPath.toString()).thenReturn("/queue/enter");
        when(cookies.getFirst("queueToken")).thenReturn(null);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        // When
        Mono<Void> result = queueWebFilter.filter(exchange, chain);

        // Then
        result.block();
        verify(chain).filter(exchange);
        verify(response, never()).setStatusCode(any());
        verify(jwtUtil, never()).validateToken(anyString());
    }

    /**
     * 활동: 유효하지 않은 토큰 (/queue/activity에서 HTTP 401).
     * 활동: 쿠키 없음 (/queue/activity에서 HTTP 400).
     * 중복성: /queue/heartbeat와 /queue/activity는 동일 로직 (uri.contains("/heartbeat") || uri.contains("/activity")) → 간접적 커버.
     * /queue/activity의 유효하지 않은 토큰과 쿠키 없는 경우를 명시적으로 테스트하려면
     * testHeartbeatWithInvalidToken, testHeartbeatWithoutToken과 유사하지만, URI가 /queue/activity로 변경
     * */

    @Test
    void testActivityWithInvalidToken() {
        // Given
        String token = "invalid.token";
        when(requestPath.toString()).thenReturn("/queue/activity");
        when(cookies.getFirst("queueToken")).thenReturn(queueTokenCookie);
        when(queueTokenCookie.getValue()).thenReturn(token);
        when(jwtUtil.validateToken(token)).thenThrow(new RuntimeException("Invalid or expired token"));
        when(bufferFactory.wrap(any(byte[].class))).thenReturn(mock(org.springframework.core.io.buffer.DataBuffer.class));
        when(response.writeWith(any(Mono.class))).thenReturn(Mono.empty());

        // When
        Mono<Void> result = queueWebFilter.filter(exchange, chain);

        // Then
        result.block();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        verify(response).writeWith(any(Mono.class));
        verify(chain, never()).filter(exchange);
        verify(bufferFactory).wrap("Invalid or expired token".getBytes());
    }

    @Test
    void testActivityWithoutToken() {
        // Given
        when(requestPath.toString()).thenReturn("/queue/activity");
        when(cookies.getFirst("queueToken")).thenReturn(null);
        when(bufferFactory.wrap(any(byte[].class))).thenReturn(mock(org.springframework.core.io.buffer.DataBuffer.class));
        when(response.writeWith(any(Mono.class))).thenReturn(Mono.empty());

        // When
        Mono<Void> result = queueWebFilter.filter(exchange, chain);

        // Then
        result.block();
        verify(response).setStatusCode(HttpStatus.BAD_REQUEST);
        verify(response).writeWith(any(Mono.class));
        verify(chain, never()).filter(exchange);
        verify(bufferFactory).wrap("Missing queueToken cookie".getBytes());
    }
}
