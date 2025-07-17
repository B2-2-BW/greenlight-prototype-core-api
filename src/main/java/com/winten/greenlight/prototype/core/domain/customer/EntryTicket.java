package com.winten.greenlight.prototype.core.domain.customer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EntryTicket {
    private Long actionId;
    private String customerId;
    private String destinationUrl;
    private Long timestamp;
    private WaitStatus waitStatus;
    private String jwt;

    public EntryTicket(WaitStatus waitStatus, String jwt) {
        this.waitStatus = waitStatus;
        this.jwt = jwt;
    }
}