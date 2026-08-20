package com.fpt.ibom.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

	private final int corePoolSize;
	private final int maxPoolSize;
	private final int queueCapacity;

	public AsyncConfig(@Value("${app.mail.executor.core-pool-size}") int corePoolSize,
			@Value("${app.mail.executor.max-pool-size}") int maxPoolSize,
			@Value("${app.mail.executor.queue-capacity}") int queueCapacity) {
		this.corePoolSize = corePoolSize;
		this.maxPoolSize = maxPoolSize;
		this.queueCapacity = queueCapacity;
	}

	@Bean("mailTaskExecutor")
	public ThreadPoolTaskExecutor mailTaskExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(corePoolSize);
		executor.setMaxPoolSize(maxPoolSize);
		executor.setQueueCapacity(queueCapacity);
		executor.setThreadNamePrefix("mail-");
		return executor;
	}
}
