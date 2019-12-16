package au.com.venilia.xgsk.util;

import java.io.IOException;

import javax.annotation.PreDestroy;
import javax.websocket.ClientEndpoint;
import javax.websocket.CloseReason;
import javax.websocket.CloseReason.CloseCodes;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;

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

	private static final Logger LOG = LoggerFactory.getLogger(SignalKClient.class);

	public final static String SELF = "vessels.self";

	private final ApplicationEventPublisher eventPublisher;

	private final ObjectMapper objectMapper;

	private Session userSession = null;

	private boolean scrolling = false;

	private final String nodeId;

	public SignalKClient(final String nodeId, final ApplicationEventPublisher eventPublisher,
			final ObjectMapper objectMapper) {

		this.nodeId = nodeId;

		this.eventPublisher = eventPublisher;
		this.objectMapper = objectMapper;
	}

	@EventListener(classes = { XBeeMessageEvent.class }, condition = "el string based on nodeId")
	private void xBeeMessage(final XBeeMessageEvent event) throws JsonProcessingException {

		final String json = objectMapper.writeValueAsString(event.getData());

		LOG.trace("Sending message: {}", json);
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

		if (!message.trim().isEmpty() && !scrolling) {

			try {

				eventPublisher.publishEvent(new SignalKMessageEvent(nodeId, objectMapper.readTree(message.trim())));
			} catch (final JsonProcessingException e) {

				LOG.error("{} thrown on receipt of SignalK message", e.getClass().getSimpleName(), e);
			}
		}
	}

	@PreDestroy
	public void destroy() {

		LOG.info(String.format("Shutting down %s", this.getClass().getSimpleName()));
		if (userSession != null && userSession.isOpen())
			try {

				userSession.close(new CloseReason(CloseCodes.NORMAL_CLOSURE, "Shutting down"));
			} catch (IOException e) {

				LOG.warn("An {} was thrown attempting to close SignalK socket - {}", e.getClass().getSimpleName(),
						e.getMessage());
			}
	}
}
