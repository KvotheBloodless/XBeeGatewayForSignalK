package au.com.venilia.xbee_gateway_for_signalk.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.listener.RetryListenerSupport;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;

import au.com.venilia.network.service.NetworkCommunicationsService;
import au.com.venilia.network.service.xbee.XBeeNetworkCommunicationsService;
import au.com.venilia.network.service.xbee.XBeeNetworkDiscoveryService;
import au.com.venilia.xbee_gateway_for_signalk.rxtx.SignalKRX;
import au.com.venilia.xbee_gateway_for_signalk.rxtx.SignalKRXManager;
import au.com.venilia.xbee_gateway_for_signalk.rxtx.XBeeTX;

@Configuration
@ComponentScan("au.com.venilia.xbee_gateway_for_signalk")
@PropertySources({ @PropertySource("classpath:application-${ENV:local}.properties") })
@EnableRetry
public class Config extends RetryListenerSupport {

	private static final Logger LOG = LoggerFactory.getLogger(Config.class);

	@Value("${xbee.portDescriptor}")
	private String portDescriptor;

	@Value("${xbee.baudRate:9600}")
	private int baudRate;

	@Bean
	public SignalKRX signalKRX() {

		LOG.info("Initialising SignalK RX");
		return new SignalKRX();
	}

	@Bean
	public SignalKRXManager signalKRXManager(final SignalKRX signalKRX) {

		LOG.info("Initialising SignalK RX manager");
		return new SignalKRXManager(signalKRX, 1000);
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

	@Bean
	public XBeeNetworkDiscoveryService xBeeNetworkDiscoveryService(final ThreadPoolTaskScheduler scheduler,
			final ApplicationEventPublisher eventPublisher) {

		return new XBeeNetworkDiscoveryService(scheduler, eventPublisher, portDescriptor, baudRate, 60L); // Each minute
	}

	@Bean
	public XBeeTX xBeeTX() {

		return new XBeeTX();
	}

	@Override
	public <T, E extends Throwable> void onError(final RetryContext context, final RetryCallback<T, E> callback,
			final Throwable throwable) {

		LOG.warn("{} - failed on retry {}; exception was a {} - {}", context.getAttribute(RetryContext.NAME),
				context.getRetryCount(), context.getLastThrowable().getCause().getClass().getSimpleName(),
				context.getLastThrowable().getCause().getMessage());
	}

	public <T, E extends Throwable> void close(RetryContext context, RetryCallback<T, E> callback,
			Throwable throwable) {

		if (context.hasAttribute(RetryContext.EXHAUSTED))
			LOG.error("{} - permantly failed after {} retries; final exception was a {} - {}",
					context.getAttribute(RetryContext.NAME), context.getRetryCount(),
					context.getLastThrowable().getCause().getClass().getSimpleName(),
					context.getLastThrowable().getCause().getMessage(), context.getLastThrowable().getCause());
	}

	@Bean
	public RetryTemplate retryTemplate() {

		final RetryTemplate retryTemplate = new RetryTemplate();

		final FixedBackOffPolicy fixedBackOffPolicy = new FixedBackOffPolicy();
		fixedBackOffPolicy.setBackOffPeriod(500L);
		retryTemplate.setBackOffPolicy(fixedBackOffPolicy);

		final SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
		retryPolicy.setMaxAttempts(2);
		retryTemplate.setRetryPolicy(retryPolicy);

		retryTemplate.setThrowLastExceptionOnExhausted(true);

		retryTemplate.registerListener(this);

		return retryTemplate;
	}

	@Bean
	public NetworkCommunicationsService moduleCommunicationsService(final ApplicationEventPublisher eventPublisher,
			final XBeeNetworkDiscoveryService xBeeNetworkDiscoveryService, final RetryTemplate retryTemplate) {

		return new XBeeNetworkCommunicationsService(eventPublisher, xBeeNetworkDiscoveryService, retryTemplate);
	}
}
