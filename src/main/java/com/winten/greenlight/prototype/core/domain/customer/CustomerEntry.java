package com.winten.greenlight.prototype.core.domain.customer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * JWT 토큰 생성 시 클레임으로 사용되는 고객 진입 정보를 담는 클래스입니다.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CustomerEntry {
    private Long actionId;
    private String customerId;
    private String destinationUrl;
    private Long timestamp;
}
