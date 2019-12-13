package au.com.venilia.xbee_gateway_for_signalk.util;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import au.com.venilia.xbee_gateway_for_signalk.event.SignalKMessageEvent;
import au.com.venilia.xbee_gateway_for_signalk.signalk.model.Delta;
import au.com.venilia.xbee_gateway_for_signalk.signalk.model.Subscribe;
import au.com.venilia.xbee_gateway_for_signalk.signalk.model.Subscribe.Format;
import au.com.venilia.xbee_gateway_for_signalk.signalk.model.Subscribe.Policy;
import au.com.venilia.xbee_gateway_for_signalk.signalk.model.Update;
import au.com.venilia.xbee_gateway_for_signalk.signalk.model.Value__2;

@ClientEndpoint
public class SignalKClient {

	private static final Logger LOG = LoggerFactory.getLogger(SignalKClient.class);

	@Autowired
	private ApplicationEventPublisher publisher;

	@Autowired
	private ObjectMapper objectMapper;

	@Value("${signalk.subscription.defaultPeriodMillis:10000}")
	private int defaultPeriod;

	private Session userSession = null;

	private boolean scrolling = false;

	public void subscribe(final String path, final Optional<Integer> period, final Optional<Integer> minPeriod)
			throws JsonProcessingException {

		final Delta message = new Delta();

		message.setContext(SignalKClient.SELF);

		final Subscribe subscribe = new Subscribe();

		subscribe.setPath(path);
		subscribe.setPeriod(period.orElse(defaultPeriod));
		subscribe.setFormat(Format.DELTA);
		subscribe.setPolicy(Policy.INSTANT);
		if (minPeriod.isPresent())
			subscribe.setMinPeriod(minPeriod.get());

		message.setSubscribe(Collections.singletonList(subscribe));

		LOG.info("Subscribing to SignalK path {}", subscribe.getPath());

		sendMessage(message);
	}

	private void sendMessage(final Delta message) throws JsonProcessingException {

		LOG.trace("Sending message: {}", objectMapper.writeValueAsString(message));
		this.userSession.getAsyncRemote().sendText(objectMapper.writeValueAsString(message));
	}

	@OnOpen
	public void onOpen(final Session userSession) {

		this.userSession = userSession;
	}

	@OnClose
	public void onClose(final Session userSession, final CloseReason reason) {

		this.userSession = null;
		// manager.openConnection();
	}

	@OnMessage
	public void onMessage(final String message) throws JsonParseException, JsonMappingException, IOException {

		if (!message.trim().isEmpty() && !scrolling) {

			try {

				final Delta delta = objectMapper.readValue(message.trim().getBytes(), Delta.class);

				for (final Update update : delta.getUpdates()) {

					for (final Value__2 value : update.getValues()) {

						try {

							final SignalKMessageEvent event = new SignalKMessageEvent(SignalKClient.SELF,
									value.getPath(), value.getValue());

							LOG.trace("SignalK event - {}", event);

							publisher.publishEvent(event);
						} catch (final EnumConstantNotPresentException e) {

							LOG.warn("Received unsupported signalK path {}", value.getPath());
						}
					}
				}
			} catch (final Exception e) {

				LOG.error("{} - {}", e.getMessage(), message.trim());
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

	public final static String SELF = "vessels.self";
}
