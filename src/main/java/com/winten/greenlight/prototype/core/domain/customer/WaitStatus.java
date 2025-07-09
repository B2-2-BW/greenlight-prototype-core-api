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
    ENTERED("입장 완료")
    ;

    private final String description; // 상태에 대한 설명

    WaitStatus(String description) {
        this.description = description;
    }
    public String description() {return description;}
}