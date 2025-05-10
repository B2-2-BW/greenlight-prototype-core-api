package com.winten.greenlight.prototype.core.domain.customer;

import com.winten.greenlight.prototype.core.db.repository.redis.customer.CustomerRepository;
import com.winten.greenlight.prototype.core.domain.event.CachedEventService;
import com.winten.greenlight.prototype.core.domain.event.Event;
import com.winten.greenlight.prototype.core.support.error.CoreException;
import com.winten.greenlight.prototype.core.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerService {
    private final CustomerRepository customerRepository;
    private final CachedEventService cachedEventService;

    public Mono<Customer> createCustomer(Customer customer, Event event) {
        return Mono.defer(() -> {
                String customerId = event.getEventName() + ":" + UUID.randomUUID().toString();
                return customerRepository.createCustomer(new Customer(customerId, customer.getScore(), WaitingPhase.WAITING));
            })
            .onErrorResume(error -> Mono.error(CoreException.of(ErrorType.DEFAULT_ERROR, error.getMessage())));
    }

    public Mono<CustomerQueueInfo> getCustomerQueueInfo(Customer customer) {
        return customerRepository.getCustomerStatus(Customer.waiting(customer.getCustomerId()))
            .zipWith(cachedEventService.getEventByName(customer.toEvent()))
            .flatMap(tuple -> {
                CustomerQueueInfo queueInfo = tuple.getT1();
                Event event = tuple.getT2();
                queueInfo.setEstimatedWaitTime(event.getQueueBackpressure() > 0 ? queueInfo.getPosition()/event.getQueueBackpressure() : -1L);
                return Mono.just(queueInfo);
            })
            .switchIfEmpty(customerRepository.getCustomerStatus(Customer.ready(customer.getCustomerId())))
            .switchIfEmpty(Mono.error(new CoreException(ErrorType.CUSTOMER_NOT_FOUND)));
    }

    public Mono<Customer> deleteCustomer(Customer customer) {
        return customerRepository.deleteCustomer(customer);
    }

    public Flux<Customer> relocateCustomer(long eventBackPressure) {
        if (eventBackPressure < 1) {
            log.info("No customers to relocated. Skipping transaction");
            return Flux.empty();
        }
        log.info("Starting customer relocation for {} customers", eventBackPressure);
        return customerRepository.getTopNCustomers(eventBackPressure)
            .flatMap(customerRepository::createCustomer)
            .doOnError(error -> log.error("Error while relocating add customer {}", error.getMessage()))
            .flatMap(customer -> {
                customer.setWaitingPhase(WaitingPhase.WAITING);
                return customerRepository.deleteCustomer(customer)
                    .doOnError(error -> log.error("Error while relocating remove customer, {}", error.getMessage()));
            })
            .doOnNext(result -> log.info("Success to relocated: {}", result));
    }
}
