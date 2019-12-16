package au.com.venilia.xgsk.config;

import java.net.URI;
import java.net.URISyntaxException;

import org.msgpack.jackson.dataformat.MessagePackFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;

import au.com.venilia.xgsk.client.SignalKClient.SignalKClientFactory;
import au.com.venilia.xgsk.client.XBeeClient.XBeeClientFactory;
import au.com.venilia.xgsk.service.XBeeNetworkDiscoveryService;

@Configuration
@ComponentScan("au.com.venilia.xgsk")
@PropertySources({ @PropertySource("classpath:application-${ENV:local}.properties") })
public class AppConfig {

	private static final Logger LOG = LoggerFactory.getLogger(AppConfig.class);

	@Value("${database}")
	private String database;

	@Bean
	public ThreadPoolTaskScheduler threadPoolTaskScheduler() {

		LOG.info("Initialising ThreadPoolTaskScheduler");

		ThreadPoolTaskScheduler threadPoolTaskScheduler = new ThreadPoolTaskScheduler();

		threadPoolTaskScheduler.setPoolSize(1);
		threadPoolTaskScheduler.setRemoveOnCancelPolicy(true);
		threadPoolTaskScheduler.setThreadNamePrefix("ThreadPoolTaskScheduler");

		return threadPoolTaskScheduler;
	}

	public static final String SIGNALK_OBJECTMAPPER = "signalk-mapper";

	@Bean(name = SIGNALK_OBJECTMAPPER)
	public ObjectMapper signalKObjectMapper() {

		LOG.info("Initialising signalk ObjectMapper");

		final ObjectMapper objectMapper = new ObjectMapper();
		objectMapper.setDefaultPropertyInclusion(Include.NON_EMPTY);

		return objectMapper;
	}

	public static final String XBEE_OBJECTMAPPER = "xbee-mapper";

	@Bean(name = XBEE_OBJECTMAPPER)
	public ObjectMapper xbeeObjectMapper() {

		LOG.info("Initialising xbee ObjectMapper");

		final ObjectMapper objectMapper = new ObjectMapper(new MessagePackFactory());
		objectMapper.setDefaultPropertyInclusion(Include.NON_EMPTY);

		return objectMapper;
	}

	@Value("${signalk.endpoint}")
	private String signalKEndpoint;

	@Bean
	public SignalKClientFactory signalKClientFactory(final ApplicationEventPublisher eventPublisher,
			final RetryTemplate retryTemplate) throws URISyntaxException {

		LOG.info("Initialising SignalKClientFactory");

		return SignalKClientFactory.instance(new URI(signalKEndpoint), eventPublisher, signalKObjectMapper(),
				retryTemplate);
	}

	@Bean
	public XBeeClientFactory xbeeClientFactory(final XBeeNetworkDiscoveryService xbeeNetworkDiscoveryService,
			final ApplicationEventPublisher eventPublisher, final RetryTemplate retryTemplate) {

		LOG.info("Initialising XBeeClientFactory");

		return XBeeClientFactory.instance(xbeeNetworkDiscoveryService.getLocalInstance(), eventPublisher,
				xbeeObjectMapper(), retryTemplate);
	}
}
