package com.winten.greenlight.prototype.core.config;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.winten.greenlight.prototype.core.support.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class QueueWebFilter implements WebFilter {
    private static final Logger logger = LoggerFactory.getLogger(QueueWebFilter.class);
    private final JwtUtil jwtUtil;
    private final ReactiveRedisTemplate<String, String> redisTemplate;

    public QueueWebFilter(JwtUtil jwtUtil, ReactiveRedisTemplate<String, String> redisTemplate) {
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String token = exchange.getRequest().getCookies().getFirst("queueToken") != null
            ? exchange.getRequest().getCookies().getFirst("queueToken").getValue()
            : null;
        String uri = exchange.getRequest().getPath().toString();

        if (uri.contains("/heartbeat") || uri.contains("/activity")) {
            if (token == null) {
                logger.warn("Missing queueToken for URI: {} from IP: {}", uri, exchange.getRequest().getRemoteAddress());
                exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
                return exchange.getResponse().writeWith(Mono.just(
                    exchange.getResponse().bufferFactory().wrap("Missing queueToken cookie".getBytes())
                ));
            }

            try {
                DecodedJWT decoded = jwtUtil.validateToken(token);
                exchange.getAttributes().put("queueId", decoded.getSubject());
                return chain.filter(exchange);
            } catch (Exception e) {
                logger.error("Invalid token for URI: {}: {}", uri, e.getMessage());
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().writeWith(Mono.just(
                    exchange.getResponse().bufferFactory().wrap("Invalid or expired token".getBytes())
                ));
            }
        }

        return chain.filter(exchange);
    }
}
