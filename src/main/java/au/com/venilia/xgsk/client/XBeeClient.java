package au.com.venilia.xgsk.client;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

import au.com.venilia.xgsk.util.ByteUtils;

public class XBeeClient implements IDataReceiveListener {

	private final Logger log;

	private ObjectMapper objectMapper;

	private final BlockingQueue<JsonNode> outgoingQueue;

	private volatile boolean run = true;

	private SignalKClient signalKClient;

	public XBeeClient(final XBeeDevice localDevice, final RemoteXBeeDevice device, final ObjectMapper objectMapper,
			final RetryTemplate retryTemplate) {

		this.log = LoggerFactory.getLogger(String.format("%s (%s)", getClass().getName(), device.getNodeID()));

		this.objectMapper = objectMapper;

		outgoingQueue = new LinkedBlockingDeque<>();
		new Thread(new Runnable() {

			@Override
			public void run() {

				while (run)
					try {

						final JsonNode jsonNode = outgoingQueue.take();
						log.trace("Sending message: {}", jsonNode);

						try {

							final byte[][] messages = ByteUtils.split(objectMapper.writeValueAsBytes(jsonNode),
									(short) 47);

							for (int i = 0; i < messages.length; i++) {

								final byte[] message = messages[i];

								retryTemplate.execute(retryContext -> {

									retryContext.setAttribute(RetryContext.NAME, device.getNodeID());

									localDevice.sendData(device, message);

									return null;
								});
							}
						} catch (final XBeeException | JsonProcessingException e) {

							// If this fails permanently, the logging will be handled by the RetryListener
							throw new RuntimeException(e);
						}
					} catch (final InterruptedException e) {

						log.error("A {} was thrown - {}", e.getClass().getSimpleName(), e.getMessage(), e);
					}
			}
		}).start();
	}

	public void signalKMessage(final JsonNode message) {

		outgoingQueue.add(message);
	}

	@Override
	public void dataReceived(final XBeeMessage xbeeMessage) {

		try {

			final JsonNode message = objectMapper.readTree(xbeeMessage.getData());
			log.debug("Message received: {}", message);

			signalKClient.xBeeMessage(message);
		} catch (final IOException e) {

			log.error("{} thrown on receipt of XBee message", e.getClass().getSimpleName(), e);
		}
	}

	@PreDestroy
	public void destroy() {

		log.info("Shutting down XBeeClient");

		run = false;
	}

	public static class XBeeClientFactory {

		private static final Logger LOG = LoggerFactory.getLogger(XBeeClientFactory.class);

		private static XBeeClientFactory INSTANCE;

		private final XBeeDevice localDevice;

		private final ObjectMapper objectMapper;
		private final RetryTemplate retryTemplate;

		public static XBeeClientFactory instance(final XBeeDevice localDevice, final ObjectMapper objectMapper,
				final RetryTemplate retryTemplate) {

			if (INSTANCE == null)
				INSTANCE = new XBeeClientFactory(localDevice, objectMapper, retryTemplate);

			return INSTANCE;
		}

		protected XBeeClientFactory(final XBeeDevice localDevice, final ObjectMapper objectMapper,
				final RetryTemplate retryTemplate) {

			this.localDevice = localDevice;

			this.objectMapper = objectMapper;
			this.retryTemplate = retryTemplate;
		}

		public XBeeClient client(final RemoteXBeeDevice device) {

			LOG.info("Creating XBeeClient for node {}", device.getNodeID());

			return new XBeeClient(localDevice, device, objectMapper, retryTemplate);
		}
	}

	public void setSignalKClient(final SignalKClient signalKClient) {

		this.signalKClient = signalKClient;
	}
}
