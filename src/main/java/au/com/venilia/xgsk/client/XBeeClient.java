package au.com.venilia.xgsk.client;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.retry.RetryContext;
import org.springframework.retry.support.RetryTemplate;

import com.digi.xbee.api.RemoteXBeeDevice;
import com.digi.xbee.api.XBeeDevice;
import com.digi.xbee.api.exceptions.XBeeException;
import com.digi.xbee.api.listeners.IDataReceiveListener;
import com.digi.xbee.api.models.XBeeMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import au.com.venilia.xgsk.event.SignalKMessageEvent;
import au.com.venilia.xgsk.event.XBeeMessageEvent;

public class XBeeClient implements IDataReceiveListener {

	private final Logger log;

	private final RemoteXBeeDevice device;

	private final ApplicationEventPublisher eventPublisher;

	private ObjectMapper objectMapper;

	private final BlockingQueue<JsonNode> outgoingQueue;

	public XBeeClient(final XBeeDevice localDevice, final RemoteXBeeDevice device,
			final ApplicationEventPublisher eventPublisher, final ObjectMapper objectMapper,
			final RetryTemplate retryTemplate) {

		this.log = LoggerFactory.getLogger(String.format("%s (%s)", getClass().getName(), device.getNodeID()));

		this.device = device;

		this.eventPublisher = eventPublisher;
		this.objectMapper = objectMapper;

		outgoingQueue = new LinkedBlockingDeque<>();
		new Thread(new Runnable() {

			@Override
			public void run() {

				while (true)
					try {

						final JsonNode jsonNode = outgoingQueue.take();

						retryTemplate.execute(retryContext -> {

							retryContext.setAttribute(RetryContext.NAME, device.getNodeID());

							try {

								log.trace("Sending message: {}", jsonNode);
								localDevice.sendData(device, objectMapper.writeValueAsBytes(jsonNode));

								return null;
							} catch (final XBeeException | JsonProcessingException e) {

								// TODO: if this fails permanently, we need to shutdown comms with the device
								// and clenup

								// If this fails permanently, the logging will be handled by the RetryListener
								throw new RuntimeException(e);
							}
						});
					} catch (final InterruptedException e) {

						log.error("A {} was thrown - {}", e.getClass().getSimpleName(), e.getMessage(), e);
					}
			}
		}).start();
	}

	@EventListener(classes = { SignalKMessageEvent.class }, condition = "el base on device.peerId")
	private void send(final SignalKMessageEvent event) {

		outgoingQueue.add(event.getData());
	}

	@Override
	public void dataReceived(final XBeeMessage xbeeMessage) {

		try {

			eventPublisher.publishEvent(
					new XBeeMessageEvent(device.getNodeID(), objectMapper.readTree(xbeeMessage.getData())));
		} catch (final IOException e) {

			log.error("{} thrown on receipt of XBee message", e.getClass().getSimpleName(), e);
		}
	}

	@PreDestroy
	public void destroy() {

		log.info("Shutting down XBeeClient");
	}

	public static class XBeeClientFactory {

		private static final Logger LOG = LoggerFactory.getLogger(XBeeClientFactory.class);

		private static XBeeClientFactory INSTANCE;

		private final XBeeDevice localDevice;

		private final ApplicationEventPublisher eventPublisher;
		private final ObjectMapper objectMapper;
		private final RetryTemplate retryTemplate;

		public static XBeeClientFactory instance(final XBeeDevice localDevice,
				final ApplicationEventPublisher eventPublisher, final ObjectMapper objectMapper,
				final RetryTemplate retryTemplate) {

			if (INSTANCE == null)
				INSTANCE = new XBeeClientFactory(localDevice, eventPublisher, objectMapper, retryTemplate);

			return INSTANCE;
		}

		protected XBeeClientFactory(final XBeeDevice localDevice, final ApplicationEventPublisher eventPublisher,
				final ObjectMapper objectMapper, final RetryTemplate retryTemplate) {

			this.localDevice = localDevice;

			this.eventPublisher = eventPublisher;
			this.objectMapper = objectMapper;
			this.retryTemplate = retryTemplate;
		}

		public XBeeClient client(final RemoteXBeeDevice device) {

			return new XBeeClient(localDevice, device, eventPublisher, objectMapper, retryTemplate);
		}
	}
}
