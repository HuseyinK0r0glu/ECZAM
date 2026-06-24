package com.eczam.audit;

public final class AuditEventType {

    // Auth
    public static final String REGISTER              = "REGISTER";
    public static final String LOGIN_SUCCESS         = "LOGIN_SUCCESS";
    public static final String LOGIN_FAILURE         = "LOGIN_FAILURE";
    public static final String LOGIN_LOCKED          = "LOGIN_LOCKED";
    public static final String LOGOUT                = "LOGOUT";
    public static final String LOGOUT_ALL            = "LOGOUT_ALL";
    public static final String TOKEN_REFRESH         = "TOKEN_REFRESH";
    public static final String TOKEN_REUSE_DETECTED  = "TOKEN_REUSE_DETECTED";

    // Password
    public static final String PASSWORD_RESET_REQUEST = "PASSWORD_RESET_REQUEST";
    public static final String PASSWORD_RESET_CONFIRM = "PASSWORD_RESET_CONFIRM";
    public static final String PASSWORD_CHANGED       = "PASSWORD_CHANGED";

    // Email verification
    public static final String EMAIL_VERIFICATION_SENT    = "EMAIL_VERIFICATION_SENT";
    public static final String EMAIL_VERIFIED             = "EMAIL_VERIFIED";

    // Account
    public static final String PROFILE_UPDATED       = "PROFILE_UPDATED";
    public static final String ACCOUNT_DELETED       = "ACCOUNT_DELETED";
    public static final String GOOGLE_LINKED         = "GOOGLE_LINKED";
    public static final String GOOGLE_LOGIN          = "GOOGLE_LOGIN";

    // Admin
    public static final String ADMIN_USER_LIST       = "ADMIN_USER_LIST";
    public static final String ADMIN_USER_LOCK       = "ADMIN_USER_LOCK";
    public static final String ADMIN_USER_UNLOCK     = "ADMIN_USER_UNLOCK";
    public static final String ADMIN_USER_DELETE     = "ADMIN_USER_DELETE";

    private AuditEventType() {}
}
