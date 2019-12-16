package au.com.venilia.xgsk.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.listener.RetryListenerSupport;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import au.com.venilia.xgsk.service.XBeeNetworkDiscoveryService;

@Configuration
@EnableRetry
public class RetryConfig extends RetryListenerSupport {

	private static final Logger LOG = LoggerFactory.getLogger(RetryConfig.class);

	@Autowired
	private XBeeNetworkDiscoveryService xbeeNetworkDiscoveryService;

	@Bean
	public RetryTemplate retryTemplate() {

		LOG.info("Initialising RetryTemplate");

		final RetryTemplate retryTemplate = new RetryTemplate();

		final ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
		backOffPolicy.setInitialInterval(200L);
		backOffPolicy.setMultiplier(2.0);
		retryTemplate.setBackOffPolicy(backOffPolicy);

		final SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
		retryPolicy.setMaxAttempts(4);
		retryTemplate.setRetryPolicy(retryPolicy);

		retryTemplate.setThrowLastExceptionOnExhausted(true);

		retryTemplate.registerListener(this);

		return retryTemplate;
	}

	@Override
	public <T, E extends Throwable> void onError(final RetryContext context, final RetryCallback<T, E> callback,
			final Throwable throwable) {

		LOG.warn("{} - failed on retry {}; exception was a {} - {}", context.getAttribute(RetryContext.NAME),
				context.getRetryCount(), context.getLastThrowable().getCause().getClass().getSimpleName(),
				context.getLastThrowable().getCause().getMessage());
	}

	@Override
	public <T, E extends Throwable> void close(final RetryContext context, final RetryCallback<T, E> callback,
			final Throwable throwable) {

		if (context.hasAttribute(RetryContext.EXHAUSTED)) {

			LOG.error("{} - permantly failed after {} retries; final exception was a {} - {}",
					context.getAttribute(RetryContext.NAME), context.getRetryCount(),
					context.getLastThrowable().getCause().getClass().getSimpleName(),
					context.getLastThrowable().getCause().getMessage(), context.getLastThrowable().getCause());

			xbeeNetworkDiscoveryService.evictNode((String) context.getAttribute(RetryContext.NAME));
		}
	}

}
