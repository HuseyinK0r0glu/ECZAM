package com.eczam.shared.web;

public final class ErrorCode {

    // Auth
    public static final String VALIDATION_FAILED      = "VALIDATION_FAILED";
    public static final String UNAUTHENTICATED        = "UNAUTHENTICATED";
    public static final String INVALID_CREDENTIALS    = "INVALID_CREDENTIALS";
    public static final String FORBIDDEN              = "FORBIDDEN";
    public static final String NOT_FOUND              = "NOT_FOUND";
    public static final String EMAIL_TAKEN            = "EMAIL_TAKEN";
    public static final String RESET_TOKEN_INVALID    = "RESET_TOKEN_INVALID";
    public static final String RATE_LIMITED           = "RATE_LIMITED";
    public static final String INTERNAL_ERROR         = "INTERNAL_ERROR";

    // Account lockout
    public static final String ACCOUNT_LOCKED         = "ACCOUNT_LOCKED";

    // Email verification
    public static final String EMAIL_NOT_VERIFIED     = "EMAIL_NOT_VERIFIED";
    public static final String VERIFICATION_TOKEN_INVALID = "VERIFICATION_TOKEN_INVALID";

    // Password
    public static final String WEAK_PASSWORD          = "WEAK_PASSWORD";
    public static final String CURRENT_PASSWORD_WRONG = "CURRENT_PASSWORD_WRONG";

    // Google OAuth
    public static final String GOOGLE_TOKEN_INVALID   = "GOOGLE_TOKEN_INVALID";
    public static final String GOOGLE_NOT_CONFIGURED  = "GOOGLE_NOT_CONFIGURED";
    public static final String GOOGLE_ACCOUNT_TAKEN   = "GOOGLE_ACCOUNT_TAKEN";

    // Token / session
    public static final String TOKEN_INVALID          = "TOKEN_INVALID";
    public static final String SESSION_NOT_FOUND      = "SESSION_NOT_FOUND";

    // Inventory / medications
    public static final String INVENTORY_BATCH_EXISTS = "INVENTORY_BATCH_EXISTS";
    public static final String INSUFFICIENT_STOCK     = "INSUFFICIENT_STOCK";
    public static final String BARCODE_NOT_FOUND      = "BARCODE_NOT_FOUND";

    private ErrorCode() {}
}
