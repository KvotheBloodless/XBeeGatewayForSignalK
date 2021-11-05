package au.com.venilia.xbee_gateway_for_signalk.rxtx;

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
public class SignalKRX {

	private static final Logger LOG = LoggerFactory.getLogger(SignalKRX.class);

	@Autowired
	private ApplicationEventPublisher publisher;

	@Autowired
	private ObjectMapper objectMapper;

	@Value("${signalk.subscription.defaultPeriodMillis:1000}")
	private int defaultPeriod;

	private Session userSession = null;

	private boolean scrolling = false;

	public void subscribe(final SignalKPath path, final Optional<Integer> period, final Optional<Integer> minPeriod)
			throws JsonProcessingException {

		final Delta message = new Delta();

		message.setContext(SignalKRX.SELF);

		final Subscribe subscribe = new Subscribe();

		subscribe.setPath(path.path());
		subscribe.setPeriod(period.orElse(defaultPeriod));
		subscribe.setFormat(Format.DELTA);
		subscribe.setPolicy(Policy.FIXED);
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

							final SignalKMessageEvent event = new SignalKMessageEvent(SignalKRX.SELF,
									SignalKPath.fromPath(value.getPath()), Integer.valueOf(value.getValue()));

							LOG.info("SignalK event - {}", event);

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

		DECK_LIGHTS_SWITCH("electrical.switches.bank.0.deck_lights.state", Optional.empty(), Optional.empty(),
				new char[] { 'p', 'u', 'v' }, true),
		WASH_DOWN_SWITCH("electrical.switches.bank.0.washdown.state", Optional.empty(), Optional.empty(),
				new char[] { 'i' }, true),
		AUTOPILOT_SWITCH("electrical.switches.bank.0.autopilot.state", Optional.empty(), Optional.empty(),
				new char[] { 'r' }, true),
		HYDRAULICS_SWITCH("electrical.switches.bank.0.hydraulics.state", Optional.empty(), Optional.empty(),
				new char[] { 'c' }, true),
		ANCHOR_LIGHT_SWITCH("electrical.switches.bank.1.anchor_light.state", Optional.empty(), Optional.empty(),
				new char[] { 'm' }, true),
		NAVIGATION_LIGHTS_SWITCH("electrical.switches.bank.1.navigation_lights.state", Optional.empty(),
				Optional.empty(), new char[] { 'q' }, true),
		STEAMING_LIGHT_SWITCH("electrical.switches.bank.1.steaming_light.state", Optional.empty(), Optional.empty(),
				new char[] { 'n' }, true),
		RED_OVER_GREEN_LIGHTS_SWITCH("electrical.switches.bank.1.red_over_green_lights.state", Optional.empty(),
				Optional.empty(), new char[] { 'o' }, true),
		FANS_SWITCH("electrical.switches.bank.2.fans.state", Optional.empty(), Optional.empty(), new char[] { 'e' },
				true),
		COMFORT_LIGHTS_SWITCH("electrical.switches.bank.2.comfort_lighting.state", Optional.empty(), Optional.empty(),
				new char[] { 'a' }, true),
		FRESH_WATER_SWITCH("electrical.switches.bank.2.fresh_water.state", Optional.empty(), Optional.empty(),
				new char[] { 'g' }, true),
		ENTERTAINMENT_SWITCH("electrical.switches.bank.2.entertainment.state", Optional.empty(), Optional.empty(),
				new char[] { 'k' }, true),
		ENGINEERING_LIGHTS_SWITCH("electrical.switches.bank.3.engineering_lighting.state", Optional.empty(),
				Optional.empty(), new char[] { 'b' }, true),
		OUTLETS_SWITCH("electrical.switches.bank.3.outlets.state", Optional.empty(), Optional.empty(),
				new char[] { 'd' }, true),
		WASTE_WATER_SWITCH("electrical.switches.bank.3.waste_water.state", Optional.empty(), Optional.empty(),
				new char[] { 'h' }, true),
		RADIOS_SWITCH("electrical.switches.bank.3.radios.state", Optional.empty(), Optional.empty(),
				new char[] { 't' }, true),
		INSTRUMENTATION_SWITCH("electrical.switches.bank.4.instrumentation.state", Optional.empty(), Optional.empty(),
				new char[] { 's' }, true),
		INTERNET_SWITCH("electrical.switches.bank.4.internet.state", Optional.empty(), Optional.empty(),
				new char[] { 'j' }, true),
		REFRIGERATION_SWITCH("electrical.switches.bank.4.refrigeration.state", Optional.empty(), Optional.empty(),
				new char[] { 'l' }, true),
		VENTILATION_SWITCH("electrical.switches.bank.4.ventilation.state", Optional.empty(), Optional.empty(),
				new char[] { 'f' }, true);

		private final String path;

		private final Optional<Class<? extends Object>> objectType;

		private final Optional<Integer> minPeriod;

		private final char[] circuits;

		private final boolean subscribe;

		SignalKPath(final String path, final Optional<Class<? extends Object>> objectType,
				final Optional<Integer> minPeriod, final char[] circuits, final boolean subscribe) {

			this.path = path;
			this.objectType = objectType;
			this.minPeriod = minPeriod;
			this.circuits = circuits;
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

		public char[] circuits() {

			return circuits;
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
