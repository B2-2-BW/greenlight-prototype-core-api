package com.winten.greenlight.prototype.core.support.error;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorType {
    DEFAULT_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.E500, "An unexpected error has occurred.", LogLevel.ERROR),
    ACTION_NOT_FOUND(HttpStatus.NOT_FOUND, ErrorCode.E404, "Action not found.", LogLevel.INFO),
    ACTION_GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, ErrorCode.E404, "Action Group not found.", LogLevel.INFO),
    CUSTOMER_NOT_FOUND(HttpStatus.NOT_FOUND, ErrorCode.E404, "Customer not found.", LogLevel.INFO),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, ErrorCode.E404, "Bad Request.", LogLevel.INFO),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, ErrorCode.E401, "Invalid token.", LogLevel.INFO),
    REDIS_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.E500, "An unexpected error has occurred while accessing data." , LogLevel.ERROR ),
    INVALID_DATA(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.E500, "Data is not valid." , LogLevel.WARN ),
    JSON_CONVERT_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.E500, "Json conversion error", LogLevel.WARN ),
    ;

    private final HttpStatus status; //HTTP 응답 코드
    private final ErrorCode code; // 고유 오류 코드
    private final String message; // 노출 메시지
    private final LogLevel logLevel;

}