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
import com.fasterxml.jackson.databind.JsonNode;

import au.com.venilia.xgsk.service.MessageTranslator;

public class XBeeClient implements IDataReceiveListener {

	private final Logger log;

	private final MessageTranslator messageTranslator;

	private final BlockingQueue<byte[]> outgoingQueue;

	private volatile boolean run = true;

	private SignalKClient signalKClient;

	public XBeeClient(final XBeeDevice localDevice, final MessageTranslator messageTranslator,
			final RemoteXBeeDevice device, final RetryTemplate retryTemplate) {

		this.log = LoggerFactory.getLogger(String.format("%s (%s)", getClass().getName(), device.getNodeID()));

		this.messageTranslator = messageTranslator;

		outgoingQueue = new LinkedBlockingDeque<>();
		new Thread(new Runnable() {

			@Override
			public void run() {

				while (run)
					try {

						final byte[] message = outgoingQueue.take();
						log.trace("Sending message: {}", message);

						try {

							retryTemplate.execute(retryContext -> {

								retryContext.setAttribute(RetryContext.NAME, device.getNodeID());

								localDevice.sendData(device, message);

								return null;
							});
						} catch (final XBeeException e) {

							// If this fails permanently, the logging will be handled by the RetryListener
							throw new RuntimeException(e);
						}
					} catch (final InterruptedException e) {

						log.error("A {} was thrown - {}", e.getClass().getSimpleName(), e.getMessage(), e);
					}
			}
		}).start();
	}

	public void signalKMessage(final byte[] message) {

		outgoingQueue.add(message);
	}

	@Override
	public void dataReceived(final XBeeMessage xbeeMessage) {

		try {

			final JsonNode message = messageTranslator.inflate(xbeeMessage.getData());
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

		private final MessageTranslator messageTranslator;

		private final RetryTemplate retryTemplate;

		public static XBeeClientFactory instance(final XBeeDevice localDevice,
				final MessageTranslator messageTranslator, final RetryTemplate retryTemplate) {

			if (INSTANCE == null)
				INSTANCE = new XBeeClientFactory(localDevice, messageTranslator, retryTemplate);

			return INSTANCE;
		}

		protected XBeeClientFactory(final XBeeDevice localDevice, final MessageTranslator messageTranslator,
				final RetryTemplate retryTemplate) {

			this.localDevice = localDevice;

			this.messageTranslator = messageTranslator;

			this.retryTemplate = retryTemplate;
		}

		public XBeeClient client(final RemoteXBeeDevice device) {

			LOG.info("Creating XBeeClient for node {}", device.getNodeID());

			return new XBeeClient(localDevice, messageTranslator, device, retryTemplate);
		}
	}

	public void setSignalKClient(final SignalKClient signalKClient) {

		this.signalKClient = signalKClient;
	}
}
