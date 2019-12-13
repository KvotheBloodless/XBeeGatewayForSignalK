package au.com.venilia.xbee_gateway_for_signalk.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;

import au.com.venilia.xbee_gateway_for_signalk.util.SignalKClient;
import au.com.venilia.xbee_gateway_for_signalk.util.SignalKClientManager;

@Configuration
@ComponentScan("au.com.venilia.xbee_gateway_for_signalk")
@PropertySources({ @PropertySource("classpath:application-${ENV:local}.properties") })
public class AppConfig {

	private static final Logger LOG = LoggerFactory.getLogger(AppConfig.class);

	@Value("${database}")
	private String database;

	@Bean
	public SignalKClient signalKClient() {

		LOG.info("Initialising SignalK client");
		return new SignalKClient();
	}

	@Bean
	public SignalKClientManager signalKClientManager(final SignalKClient signalKClient) {

		LOG.info("Initialising SignalK client manager");
		return new SignalKClientManager(signalKClient, 1000);
	}

	@Bean
	public ThreadPoolTaskScheduler threadPoolTaskScheduler() {

		LOG.info("Initialising task scheduler");

		ThreadPoolTaskScheduler threadPoolTaskScheduler = new ThreadPoolTaskScheduler();

		threadPoolTaskScheduler.setPoolSize(1);
		threadPoolTaskScheduler.setRemoveOnCancelPolicy(true);
		threadPoolTaskScheduler.setThreadNamePrefix("ThreadPoolTaskScheduler");

		return threadPoolTaskScheduler;
	}

	@Bean
	public ObjectMapper objectMapper() {

		LOG.info("Initialising object mapper");

		ObjectMapper objectMapper = new ObjectMapper();

		objectMapper.setDefaultPropertyInclusion(Include.NON_EMPTY);

		return objectMapper;
	}
}
