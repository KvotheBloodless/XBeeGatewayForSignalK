package au.com.venilia.xgsk.client;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

import javax.annotation.PreDestroy;
import javax.websocket.ClientEndpoint;
import javax.websocket.CloseReason;
import javax.websocket.CloseReason.CloseCodes;
import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.RetryContext;
import org.springframework.retry.support.RetryTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import au.com.venilia.xgsk.service.MessageTranslator;

@ClientEndpoint
public class SignalKClient {

	private final Logger log;

	private final String nodeId;

	private final ObjectMapper objectMapper;

	private final MessageTranslator messageTranslator;

	private final BlockingQueue<JsonNode> outgoingQueue;

	private final RetryTemplate retryTemplate;
	private Session userSession = null;

	private volatile boolean run = true;

	private XBeeClient xBeeClient;

	public SignalKClient(final String nodeId, final MessageTranslator messageTranslator,
			final ObjectMapper objectMapper, final RetryTemplate retryTemplate) {

		this.log = LoggerFactory.getLogger(String.format("%s (%s)", getClass().getName(), nodeId));

		this.nodeId = nodeId;

		this.messageTranslator = messageTranslator;
		this.objectMapper = objectMapper;

		this.retryTemplate = retryTemplate;

		outgoingQueue = new LinkedBlockingDeque<>();
	}

	public void xBeeMessage(final JsonNode message) throws JsonProcessingException {

		outgoingQueue.add(message);
	}

	@OnOpen
	public void onOpen(final Session userSession) {

		this.userSession = userSession;

		new Thread(new Runnable() {

			@Override
			public void run() {

				while (run)
					try {

						final JsonNode jsonNode = outgoingQueue.take();

						retryTemplate.execute(retryContext -> {

							retryContext.setAttribute(RetryContext.NAME, nodeId);

							try {

								log.trace("Sending message: {}", jsonNode);
								userSession.getAsyncRemote().sendText(objectMapper.writeValueAsString(jsonNode));

								return null;
							} catch (final JsonProcessingException e) {

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

	@OnMessage
	public void onMessage(final String data) {

		if (!data.trim().isEmpty()) {

			try {

				final JsonNode message = objectMapper.readTree(data.trim());
				log.debug("Message received: {}", message);

				xBeeClient.signalKMessage(messageTranslator.deflate(message));
			} catch (final JsonProcessingException e) {

				log.error("{} thrown on receipt of SignalK message", e.getClass().getSimpleName(), e);
			}
		}
	}

	@PreDestroy
	public void destroy() {

		log.info("Shutting down SignalKClient");

		run = false;

		if (userSession != null && userSession.isOpen())
			try {

				userSession.close(new CloseReason(CloseCodes.NORMAL_CLOSURE, "Shutting down"));
			} catch (IOException e) {

				log.warn("An {} was thrown attempting to close SignalK socket - {}", e.getClass().getSimpleName(),
						e.getMessage());
			}
	}

	public static class SignalKClientFactory {

		private static final Logger LOG = LoggerFactory.getLogger(SignalKClientFactory.class);

		private static SignalKClientFactory INSTANCE;

		private final URI endpointUri;
		private final MessageTranslator messageTranslator;
		private final ObjectMapper objectMapper;
		private final RetryTemplate retryTemplate;

		public static SignalKClientFactory instance(final URI endpointUri, final MessageTranslator messageTranslator,
				final ObjectMapper objectMapper, final RetryTemplate retryTemplate) {

			if (INSTANCE == null)
				INSTANCE = new SignalKClientFactory(endpointUri, messageTranslator, objectMapper, retryTemplate);

			return INSTANCE;
		}

		protected SignalKClientFactory(final URI endpointUri, final MessageTranslator messageTranslator,
				final ObjectMapper objectMapper, final RetryTemplate retryTemplate) {

			this.endpointUri = endpointUri;
			this.messageTranslator = messageTranslator;
			this.objectMapper = objectMapper;
			this.retryTemplate = retryTemplate;
		}

		public SignalKClient client(final String nodeId) throws DeploymentException, IOException {

			LOG.info("Creating SignalKClient for node {}", nodeId);

			final SignalKClient client = new SignalKClient(nodeId, messageTranslator, objectMapper, retryTemplate);

			LOG.debug("Opening websocket to SignalK server at {} for node {}", endpointUri, nodeId);

			final WebSocketContainer container = ContainerProvider.getWebSocketContainer();
			container.connectToServer(client, endpointUri);

			LOG.debug("Successfully opened websocket to SignalK server at {}", endpointUri);

			return client;
		}
	}

	public void setXBeeClient(final XBeeClient xBeeClient) {

		this.xBeeClient = xBeeClient;
	}
}
