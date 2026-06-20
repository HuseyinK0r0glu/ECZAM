package com.eczam.shared.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

@ConfigurationProperties(prefix = "eczam.jwt")
public record JwtProperties(String secret, Duration accessTtl, Duration refreshTtl, Duration resetTtl) {}
