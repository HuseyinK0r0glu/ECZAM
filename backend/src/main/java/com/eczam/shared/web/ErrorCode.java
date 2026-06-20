package com.eczam.shared.web;

public final class ErrorCode {
    public static final String VALIDATION_FAILED   = "VALIDATION_FAILED";
    public static final String UNAUTHENTICATED     = "UNAUTHENTICATED";
    public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
    public static final String FORBIDDEN           = "FORBIDDEN";
    public static final String NOT_FOUND           = "NOT_FOUND";
    public static final String EMAIL_TAKEN         = "EMAIL_TAKEN";
    public static final String INVENTORY_BATCH_EXISTS = "INVENTORY_BATCH_EXISTS";
    public static final String INSUFFICIENT_STOCK  = "INSUFFICIENT_STOCK";
    public static final String BARCODE_NOT_FOUND   = "BARCODE_NOT_FOUND";
    public static final String RESET_TOKEN_INVALID = "RESET_TOKEN_INVALID";
    public static final String RATE_LIMITED        = "RATE_LIMITED";
    public static final String INTERNAL_ERROR      = "INTERNAL_ERROR";
    private ErrorCode() {}
}
