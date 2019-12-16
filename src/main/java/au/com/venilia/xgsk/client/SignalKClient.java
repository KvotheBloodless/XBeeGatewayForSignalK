package au.com.venilia.xgsk.client;

import java.io.IOException;
import java.net.URI;

import javax.annotation.PreDestroy;
import javax.websocket.ClientEndpoint;
import javax.websocket.CloseReason;
import javax.websocket.CloseReason.CloseCodes;
import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import au.com.venilia.xgsk.event.SignalKMessageEvent;
import au.com.venilia.xgsk.event.XBeeMessageEvent;

@ClientEndpoint
public class SignalKClient {

	private final Logger log;

	public final static String SELF = "vessels.self";

	private final ApplicationEventPublisher eventPublisher;

	private final ObjectMapper objectMapper;

	private Session userSession = null;

	private final String nodeId;

	public SignalKClient(final String nodeId, final ApplicationEventPublisher eventPublisher,
			final ObjectMapper objectMapper) {

		this.log = LoggerFactory.getLogger(String.format("%s (%s)", getClass().getName(), nodeId));

		this.nodeId = nodeId;

		this.eventPublisher = eventPublisher;
		this.objectMapper = objectMapper;
	}

	@EventListener(classes = { XBeeMessageEvent.class }, condition = "el string based on nodeId")
	private void xBeeMessage(final XBeeMessageEvent event) throws JsonProcessingException {

		final String json = objectMapper.writeValueAsString(event.getData());

		log.trace("Sending message: {}", json);
		this.userSession.getAsyncRemote().sendText(json);
	}

	@OnOpen
	public void onOpen(final Session userSession) {

		this.userSession = userSession;
	}

	@OnClose
	public void onClose(final Session userSession, final CloseReason reason) {

		this.userSession = null;
	}

	@OnMessage
	public void onMessage(final String message) {

		if (!message.trim().isEmpty()) {

			try {

				eventPublisher.publishEvent(new SignalKMessageEvent(nodeId, objectMapper.readTree(message.trim())));
			} catch (final JsonProcessingException e) {

				log.error("{} thrown on receipt of SignalK message", e.getClass().getSimpleName(), e);
			}
		}
	}

	@PreDestroy
	public void destroy() {

		log.info("Shutting down SignalKClient");

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
		private final ApplicationEventPublisher eventPublisher;
		private final ObjectMapper objectMapper;

		public static SignalKClientFactory instance(final URI endpointUri,
				final ApplicationEventPublisher eventPublisher, final ObjectMapper objectMapper) {

			if (INSTANCE == null)
				INSTANCE = new SignalKClientFactory(endpointUri, eventPublisher, objectMapper);

			return INSTANCE;
		}

		protected SignalKClientFactory(final URI endpointUri, final ApplicationEventPublisher eventPublisher,
				final ObjectMapper objectMapper) {

			this.endpointUri = endpointUri;
			this.eventPublisher = eventPublisher;
			this.objectMapper = objectMapper;
		}

		public SignalKClient client(final String peerId) throws DeploymentException, IOException {

			final SignalKClient client = new SignalKClient(peerId, eventPublisher, objectMapper);

			LOG.info("Opening websocket to SignalK server at {}", endpointUri);

			final WebSocketContainer container = ContainerProvider.getWebSocketContainer();
			container.connectToServer(client, endpointUri);

			LOG.info("Successfully opened websocket to SignalK server at {}", endpointUri);

			return client;
		}
	}
}
