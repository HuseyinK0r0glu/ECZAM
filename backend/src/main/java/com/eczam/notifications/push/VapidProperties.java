package com.eczam.notifications.push;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "eczam.vapid")
public record VapidProperties(String publicKey, String privateKey, String subject) {}
