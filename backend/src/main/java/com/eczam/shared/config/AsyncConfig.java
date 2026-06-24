package com.eczam.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.Executor;

import static org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW;

@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Dedicated executor for the audit log so it doesn't compete with
     * the application's general async work (leaflet indexing, email).
     * Returned as TaskExecutor so AuditService can inject it directly.
     */
    @Bean("auditExecutor")
    public TaskExecutor auditExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(2);
        exec.setMaxPoolSize(4);
        exec.setQueueCapacity(500);
        exec.setThreadNamePrefix("audit-");
        exec.setWaitForTasksToCompleteOnShutdown(true);
        exec.setAwaitTerminationSeconds(10);
        exec.initialize();
        return exec;
    }

    /**
     * General-purpose async executor used by @Async methods that don't
     * specify a named executor (e.g. LeafletIndexer, email sending).
     */
    @Bean("taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(4);
        exec.setMaxPoolSize(8);
        exec.setQueueCapacity(200);
        exec.setThreadNamePrefix("async-");
        exec.setWaitForTasksToCompleteOnShutdown(true);
        exec.setAwaitTerminationSeconds(30);
        exec.initialize();
        return exec;
    }

    /**
     * REQUIRES_NEW TransactionTemplate used by AuditService to wrap each
     * audit INSERT in its own independent transaction.
     */
    @Bean
    public TransactionTemplate requiresNewTx(PlatformTransactionManager txManager) {
        TransactionTemplate tt = new TransactionTemplate(txManager);
        tt.setPropagationBehavior(PROPAGATION_REQUIRES_NEW);
        return tt;
    }
}
