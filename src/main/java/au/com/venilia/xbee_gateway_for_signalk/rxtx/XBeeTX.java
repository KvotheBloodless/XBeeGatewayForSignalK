package au.com.venilia.xbee_gateway_for_signalk.rxtx;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;

import com.google.common.collect.Lists;

import au.com.venilia.network.service.NetworkCommunicationsService;
import au.com.venilia.network.service.NetworkCommunicationsService.PeerGroup;
import au.com.venilia.xbee_gateway_for_signalk.event.SignalKMessageEvent;

public class XBeeTX {

	private static final Logger LOG = LoggerFactory.getLogger(XBeeTX.class);

	@Autowired
	private NetworkCommunicationsService networkCommunicationsService;

	@EventListener
	public void something(final SignalKMessageEvent event) {

		final List<String> commands = buildCommands(event);
		LOG.debug("Command(s): {}", commands);

		commands.forEach(c -> {

			final byte[] buf = c.toString().getBytes();
			networkCommunicationsService.send(PeerGroup.SWITCHES, buf);
		});
	}

	private static List<String> buildCommands(final SignalKMessageEvent event) {

		final List<String> commands = Lists.newArrayList();
		for (char c : event.getCircuits()) {

			commands.add(String.format("%c%d;", c, event.getValue()));
		}

		return commands;
	}
}
