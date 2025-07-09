package com.winten.greenlight.prototype.core.api.controller.queue.dto;

import com.winten.greenlight.prototype.core.domain.customer.WaitStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * API 응답을 위한 DTO (Data Transfer Object) 클래스입니다.
 * 클라이언트에게 대기열 진입 결과 및 상태를 전달하는 데 사용됩니다。
 * 위치: com.winten.greenlight.prototype.core.api.controller.queue.dto
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CheckOrEnterResponse {
    /**
     * status: 현재 대기열 상태를 나타내는 문자열입니다.
     * - "WAITING": 대기열에 진입하여 순서를 기다리는 중.
     * - "ALLOWED": 대기열을 통과하여 서비스 진입이 허용됨.
     * - "EXISTING": 이미 유효한 토큰을 가지고 있음 (재접속 시).
     * - "DISABLED": 해당 액션이 현재 비활성화되어 대기열 진입 불가.
     * - "ACTION_NOT_FOUND": 요청한 액션(actionUrl)을 시스템에서 찾을 수 없음.(ACTION_NOT_FOUND는 대기열의 상태가 아니라, 요청 처리 중 발생한 예외적인 상황)
     *  DTO의 `status`는 `WaitStatus` Enum보다 더 많은 상태를 표현해야 함.
     *  WaitStatus는 핵심 비즈니스 상태 정의
     *  WaitStatus Enum의 책임은 "대기열의 핵심 상태를 정의하는 것" 하나여야 합니다.
     *
     *  아래의 status값은 api응답 전용(클라이언트에게 어떤 결과를 알려줘야 하는가?)
     *
     */
    private String status; // "WAITING", "ALLOWED", "DISABLED", "ACTION_NOT_FOUND"
    private String token;
    private Long rank; // 대기 중일 경우의 예상 순번

    /**
     * WaitStatus Enum에 ACTION_NOT_FOUND, DISABLED 등을 추가하는 것은, 주방장에게 "요리 상태"가 아닌 "손님 응대 매뉴얼"까지
     *   외우게 하는 것과 같습니다. 주방장은 이제 "요리 중", "요리 완료" 뿐만 아니라 "없는 메뉴", "재료 소진" 같은 상태까지
     *   알아야 합니다. 주방장의 책임이 너무 많아지고 복잡해집니다.
     * -> 이 내용은 설명을 위해 넣음. 최종적으로 지울 예정.
     * */
}
