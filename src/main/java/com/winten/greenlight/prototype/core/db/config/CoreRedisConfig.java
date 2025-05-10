package com.winten.greenlight.prototype.core.db.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.winten.greenlight.prototype.core.db.repository.redis.event.EventEntity;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.tracing.MicrometerTracing;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.*;

@Configuration
public class CoreRedisConfig {

    @Bean
    public ClientResources clientResources(ObservationRegistry observationRegistry) {
        return ClientResources.builder()
            .tracing(new MicrometerTracing(observationRegistry, "redis-service"))
            .build();
    }

    @Bean
    public LettuceConnectionFactory lettuceConnectionFactory(RedisProperties properties, ClientResources clientResources) {
        var config = new RedisStandaloneConfiguration(properties.getHost(), properties.getPort());
        config.setPassword(properties.getPassword());
        var clientConfig = LettuceClientConfiguration.builder()
            .clientResources(clientResources)
            .build();
        return new LettuceConnectionFactory(config, clientConfig);
    }

    @Bean
    public ReactiveRedisTemplate<String, EventEntity> reactiveEventRedisTemplate(LettuceConnectionFactory factory, ObjectMapper objectMapper) {
        RedisSerializer<String> keySerializer = new StringRedisSerializer();
        RedisSerializer<EventEntity> valueSerializer = new Jackson2JsonRedisSerializer<>(objectMapper, EventEntity.class);
        RedisSerializationContext<String, EventEntity> serializationContext = RedisSerializationContext
            .<String, EventEntity>newSerializationContext(keySerializer)
            .value(valueSerializer)
            .build();
        return new ReactiveRedisTemplate<>(factory, serializationContext);
    }
}
