package com.eczam.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** @EnableAsync is handled by AsyncConfig which also declares the thread pool beans. */
@Configuration
@EnableScheduling
public class SchedulingConfig {}
