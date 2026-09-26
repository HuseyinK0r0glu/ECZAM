package com.eczam.shared.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApi(ApiException ex) {
        ApiError error = ex.fields() != null
                ? new ApiError(ex.code(), ex.getMessage(), ex.fields())
                : new ApiError(ex.code(), ex.getMessage());
        return ResponseEntity.status(ex.status()).body(ApiResponse.fail(error));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.fail(new ApiError(ErrorCode.VALIDATION_FAILED, "Validation failed", fields)));
    }

    // Also covers a request body that fails to deserialize (e.g. an enum field sent
    // with a value outside its accepted set) — that's a client input error, and per
    // CLAUDE.md's "validate every endpoint; on failure return 422" convention it
    // shouldn't fall through to a 500 any more than a bad query-param value does.
    @ExceptionHandler({ MethodArgumentTypeMismatchException.class, DateTimeParseException.class,
                        HttpMessageNotReadableException.class })
    public ResponseEntity<ApiResponse<Void>> handleBadValue(Exception ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.fail(new ApiError(ErrorCode.VALIDATION_FAILED, "Invalid request value")));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuth(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.fail(new ApiError(ErrorCode.UNAUTHENTICATED, "Authentication required")));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.fail(new ApiError(ErrorCode.FORBIDDEN, "Access denied")));
    }

    // A request that doesn't match any @RequestMapping falls through Spring's static-
    // resource handler, which throws this — without it, an unmapped/mistyped route
    // (e.g. a typo'd path) fell into handleOther() below and returned a scary 500
    // instead of a plain 404.
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoRoute(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail(new ApiError(ErrorCode.NOT_FOUND, "No such endpoint")));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleOther(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(new ApiError(ErrorCode.INTERNAL_ERROR, "Unexpected error")));
    }
}
