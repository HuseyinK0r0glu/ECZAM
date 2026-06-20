package com.eczam.notifications;

import com.eczam.notifications.push.VapidProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(VapidProperties.class)
public class NotificationsConfig {}
