package com.winten.greenlight.prototype.core.api.controller.queue.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CheckOrEnterResponse {
    private final String status;
    private final String token;
    private final Long rank;
}
