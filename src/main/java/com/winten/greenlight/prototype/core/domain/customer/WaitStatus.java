package com.winten.greenlight.prototype.core.domain.customer;

public enum WaitStatus {
    /**
     * 대기 중: 현재 대기열에서 자신의 순서를 기다리는 상태입니다.
     */
    WAITING("대기 중"),

    /**
     * 입장 허용: 자신의 순서가 되어 입장이 허용된 상태입니다.
     *
     *
     */
    READY("입장 가능"),

    /**
     * 입장 완료: 서비스에 입장한 최종 상태입니다.
     */
    ENTERED("입장 완료"),

    /**
     * 대기열 적용 대상이 아님: 특정 규칙에 의해 대기열을 우회한 상태입니다.
     */
    BYPASSED("대기열 우회"),

    /**
     * 비활성화: 액션 또는 액션 그룹이 현재 비활성화된 상태입니다.
     */
    DISABLED("비활성화")
    ;

    private final String description; // 상태에 대한 설명

    WaitStatus(String description) {
        this.description = description;
    }
    public String description() {return description;}
}