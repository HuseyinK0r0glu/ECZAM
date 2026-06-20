package com.eczam.shared.web;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ApiResponse<T>(T data, Meta meta, ApiError error) {
    public static <T> ApiResponse<T> ok(T data) { return new ApiResponse<>(data, null, null); }
    public static <T> ApiResponse<T> ok(T data, Meta meta) { return new ApiResponse<>(data, meta, null); }
    public static <T> ApiResponse<T> fail(ApiError error) { return new ApiResponse<>(null, null, error); }
}
