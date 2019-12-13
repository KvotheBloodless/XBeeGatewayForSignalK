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
import au.com.venilia.xbee_gateway_for_signalk.signalk.model.Attitude;
import au.com.venilia.xbee_gateway_for_signalk.signalk.model.Delta;
import au.com.venilia.xbee_gateway_for_signalk.signalk.model.Notification;
import au.com.venilia.xbee_gateway_for_signalk.signalk.model.Position;
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

	public void subscribe(final SignalKPath path, final Optional<Integer> period, final Optional<Integer> minPeriod)
			throws JsonProcessingException {

		final Delta message = new Delta();

		message.setContext(SignalKClient.SELF);

		final Subscribe subscribe = new Subscribe();

		subscribe.setPath(path.path());
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
									SignalKPath.fromPath(value.getPath()), value.getValue());

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

	public static enum SignalKPath {

		AUTOPILOT_STATE("steering.autopilot.state", Optional.empty(), Optional.empty(), false),
		AUTOPILOT_MODE("steering.autopilot.mode", Optional.empty(), Optional.empty(), false),
		APPARENT_WIND_SPEED("environment.wind.speedApparent", Optional.empty(), Optional.of(2000), true),
		APPARENT_WIND_ANGLE("environment.wind.angleApparent", Optional.empty(), Optional.of(2000), true),
		HEAVE("environment.heave", Optional.empty(), Optional.empty(), false),
		COURSE_OVER_GROUND("navigation.courseOverGround", Optional.empty(), Optional.empty(), false),
		DEPTH_BELOW_SURFACE("environment.depth.belowSurface", Optional.empty(), Optional.of(2000), true),
		DEPTH_BELOW_TRANSDUCER("environment.depth.belowTransducer", Optional.empty(), Optional.empty(), false),
		SURFACE_TO_TRANSDUCER("environment.depth.surfaceToTransducer", Optional.empty(), Optional.empty(), false),
		POSITION("navigation.position", Optional.of(Position.class), Optional.of(4000), true),
		ATTITUDE("navigation.attitude", Optional.of(Attitude.class), Optional.empty(), false),
		HEADING_TRUE("navigation.headingTrue", Optional.empty(), Optional.empty(), false),
		HEADING_MAGNETIC("navigation.headingMagnetic", Optional.empty(), Optional.of(4000), true),
		RATE_OF_TURN("navigation.rateOfTurn", Optional.empty(), Optional.empty(), false),
		SOG("environment.navigation.speedOverGround", Optional.empty(), Optional.empty(), false),
		DATE_TIME("environment.navigation.datetime", Optional.empty(), Optional.of(60000), true),
		COOLANT_TEMP("environment.propulsion.port.coolantTemperature", Optional.empty(), Optional.empty(), false),
		OIL_TEMP("environment.propulsion.port.oilTemperature", Optional.empty(), Optional.empty(), false),
		OIL_PRESSURE("environment.propulsion.port.oilPressure", Optional.empty(), Optional.empty(), false),
		RUDDER_ANGLE("steering.rudderAngle", Optional.empty(), Optional.empty(), false),
		NOTIFICATION_MOB("notifications.mob", Optional.of(Notification.class), Optional.empty(), true),
		NOTIFICATION_ANCHOR_ALARM("notifications.navigation.anchor.currentRadius", Optional.of(Notification.class),
				Optional.empty(), true);

		private final String path;

		private final Optional<Class<? extends Object>> objectType;

		private final Optional<Integer> minPeriod;

		private final boolean subscribe;

		SignalKPath(final String path, final Optional<Class<? extends Object>> objectType,
				final Optional<Integer> minPeriod, final boolean subscribe) {

			this.path = path;
			this.objectType = objectType;
			this.minPeriod = minPeriod;
			this.subscribe = subscribe;
		}

		public String path() {

			return path;
		}

		public Optional<Class<? extends Object>> objectType() {

			return objectType;
		}

		public Optional<Integer> getMinPeriod() {

			return minPeriod;
		}

		public boolean subscribe() {

			return subscribe;
		}

		public static SignalKPath fromPath(final String path) {

			for (final SignalKPath signalKPath : values()) {

				if (signalKPath.path().equals(path))
					return signalKPath;
			}

			throw new EnumConstantNotPresentException(SignalKPath.class, path);
		}
	}
}
