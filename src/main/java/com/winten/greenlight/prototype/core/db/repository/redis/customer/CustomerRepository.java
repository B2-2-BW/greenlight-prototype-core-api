package com.winten.greenlight.prototype.core.db.repository.redis.customer;

import com.winten.greenlight.prototype.core.domain.customer.Customer;
import com.winten.greenlight.prototype.core.domain.customer.CustomerQueueInfo;
import com.winten.greenlight.prototype.core.domain.customer.CustomerService;
import com.winten.greenlight.prototype.core.domain.customer.WaitingPhase;
import com.winten.greenlight.prototype.core.support.error.CoreException;
import com.winten.greenlight.prototype.core.support.error.ErrorType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Repository
public class CustomerRepository {
    private final ReactiveRedisTemplate<String, String> redisTemplate;

    public CustomerRepository(ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Mono<Customer> createCustomer(Customer customer) {
        return redisTemplate.opsForZSet()
                    .add(customer.getWaitingPhase().queueName(), customer.getCustomerId(), customer.getScore())
                    .<Customer>handle((success, sink) -> {
                        if (Boolean.TRUE.equals(success)) {
                            sink.next(customer);
                        } else {
                            sink.error(CoreException.of(ErrorType.REDIS_ERROR, "Customer Not Found"));
                        }
                    })
                    .onErrorResume(e -> Mono.error(CoreException.of(ErrorType.REDIS_ERROR, e.getMessage())));
    }
    public Mono<CustomerQueueInfo> getCustomerStatus(Customer customer) {
        String customerId = customer.getCustomerId();
        WaitingPhase waitingPhase = customer.getWaitingPhase();
        return Mono.zip(
                    redisTemplate.opsForZSet().rank(waitingPhase.queueName(), customerId),  // 위치
                    redisTemplate.opsForZSet().size(waitingPhase.queueName())               // 큐 크기
            ).map(tuple -> {
                Long position = tuple.getT1();
                Long queueSize = tuple.getT2();
                return CustomerQueueInfo.builder()
                        .customerId(customerId)
                        .position(position)
                        .queueSize(queueSize)
                        .waitingPhase(waitingPhase)
                        .estimatedWaitTime(null) // Service에서 계산
                        .build();
            });
    }

    public Mono<Customer> deleteCustomer(Customer customer) {
        return Mono.just(CustomerZSetEntity.of(customer))
                .flatMap(entity -> redisTemplate.opsForZSet()
                        //삭제처리
                        .remove(customer.getWaitingPhase().queueName(), entity.getCustomerId())
                        //삭제된 데이터 없는 경우 Mono.empty() 반환
                        .flatMap(removedCount -> removedCount > 0 ? Mono.just(customer) : Mono.empty())
                );
    }

    public Flux<Customer> getTopNCustomers(long eventBackPressure) {
        return redisTemplate.opsForZSet()
                .rangeWithScores(WaitingPhase.WAITING.queueName(), Range.closed(0L, ((long)eventBackPressure-1L)))
                .map(tuple -> {
                    log.info("GetValue(): {}", tuple.getValue());
                    return new Customer(tuple.getValue(), tuple.getScore(), WaitingPhase.READY);
                })
                //.map(tuple -> new Customer(tuple.getValue(), tuple.getScore(), WaitingPhase.READY))
                .onErrorResume(e -> Mono.error(CoreException.of(ErrorType.REDIS_ERROR, e.getMessage())));
    }
}
