package ru.normacontrol.infrastructure.config;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Конфигурация асинхронного выполнения задач.
 */
@EnableAsync
@Configuration
public class AsyncConfig {

    @Bean(name = "checkExecutor")
    public Executor checkExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("CheckExecutor-");
        executor.setTaskDecorator(contextCopyingTaskDecorator());
        executor.initialize();
        return executor;
    }

    @Bean(name = "auditExecutor")
    public Executor auditExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("AuditExecutor-");
        executor.setTaskDecorator(contextCopyingTaskDecorator());
        executor.initialize();
        return executor;
    }

    private TaskDecorator contextCopyingTaskDecorator() {
        return runnable -> {
            RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
            Map<String, String> mdcContext = MDC.getCopyOfContextMap();
            return () -> {
                try {
                    if (requestAttributes != null) {
                        RequestContextHolder.setRequestAttributes(requestAttributes);
                    }
                    if (mdcContext != null) {
                        MDC.setContextMap(mdcContext);
                    }
                    runnable.run();
                } finally {
                    RequestContextHolder.resetRequestAttributes();
                    MDC.clear();
                }
            };
        };
    }
}
