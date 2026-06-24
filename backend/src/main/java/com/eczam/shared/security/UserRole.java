package com.eczam.shared.security;

public enum UserRole {
    USER,
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
