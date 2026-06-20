package com.eczam.shared.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String code, String message, Map<String, String> fields) {
    public ApiError(String code, String message) { this(code, message, null); }
}
