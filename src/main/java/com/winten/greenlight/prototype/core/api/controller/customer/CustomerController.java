package com.winten.greenlight.prototype.core.api.controller.customer;

import com.winten.greenlight.prototype.core.domain.customer.CustomerEntry;
import com.winten.greenlight.prototype.core.domain.customer.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/action")
@RequiredArgsConstructor
public class CustomerController {
    private final CustomerService customerService;

    // TODO
    //  1. 서비스 입장
    @PostMapping("{actionId}/check-in")
    public Mono<ResponseEntity<CustomerEntryResponse>> requestEntry(
            @RequestHeader("x-greenlight-ticket") String greenlightTicket,
            @RequestBody CustomerEntryRequest request
    ) {

        long timestamp = System.currentTimeMillis();
        return customerService.requestEntry(request.toCustomerEntry(timestamp))
                .map(entry -> CustomerEntryResponse.of(entry))
                .map(res -> ResponseEntity.ok(res));
    }

    // TODO
    //  2. 입장권 검증요청
    @PostMapping("verify")
    public Mono<ResponseEntity<TicketVerificationResponse>> verifyTicket(
            @RequestHeader("Authorization") String authorization
    ) {
        return customerService.verifyTicket(authorization)
                .map(result -> new TicketVerificationResponse())
                .map(res -> ResponseEntity.ok(res));
    }
}