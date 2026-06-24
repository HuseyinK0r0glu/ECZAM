package com.eczam.shared.web;

import org.springframework.http.HttpStatus;

import java.util.Map;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Map<String, String> fields;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
        this.fields = null;
    }

    public ApiException(HttpStatus status, String code, String message, Map<String, String> fields) {
        super(message);
        this.status = status;
        this.code = code;
        this.fields = fields;
    }

    public HttpStatus status() { return status; }
    public String code()       { return code; }
    public Map<String, String> fields() { return fields; }

    // ---- Factory methods ----

    public static ApiException of(HttpStatus status, String code, String message) {
        return new ApiException(status, code, message);
    }

    public static ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    public static ApiException notFound(String message) {
        return notFound(ErrorCode.NOT_FOUND, message);
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }

    public static ApiException unprocessable(String code, String message,
                                             String fieldName, String fieldMessage) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, message,
                Map.of(fieldName, fieldMessage));
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_CREDENTIALS, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, message);
    }

    public static ApiException locked(String message) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.ACCOUNT_LOCKED, message);
    }
}
