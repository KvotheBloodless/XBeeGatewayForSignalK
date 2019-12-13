package au.com.venilia.xbee_gateway_for_signalk;

import java.io.IOException;
import java.net.URI;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceListener;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import au.com.venilia.xbee_gateway_for_signalk.util.SignalKClientManager;

public class App implements ServiceListener {

	static {

		// Enable using system proxy if set
		System.setProperty("java.net.useSystemProxies", "true");
	}

	private static final Logger LOG = LoggerFactory.getLogger(App.class);

	private static ApplicationContext context;

	private static SignalKClientManager signalKClientManager;

	public App(/* include parameters as required */) {

	}

	public static void main(String... args) throws IOException {

		System.out.println("=========================");
		System.out.println("= XBeeGatewayForSignalK =");
		System.out.println("=========================");

		try {

			final CommandLine commandLine = parseCliArguments(args);

			LOG.info("Starting with parameters: [signalk: {}]", commandLine.getOptionValue("signalk"));

			context = new AnnotationConfigApplicationContext("au.com.venilia.xbee_gateway_for_signalk");

			signalKClientManager = context.getBean(SignalKClientManager.class);

			if (commandLine.getOptionValue("signalk") != null)
				signalKClientManager.setEndpointUri(URI
						.create("ws://" + commandLine.getOptionValue("signalk") + "/signalk/v1/stream?subscribe=none"));
		} catch (final ParseException parseException) {

			System.exit(-1);
		}

		// Create a JmDNS instance and add a service listener
		final JmDNS jmDNS = JmDNS.create();
		jmDNS.addServiceListener("_signalk-ws._tcp.local.", new App());
	}

	private static CommandLine parseCliArguments(String... args) throws ParseException {

		final Options options = new Options();

		final Option a = new Option("k", "signalk", true, "SignalK host:port");
		a.setRequired(false);
		options.addOption(a);

		final CommandLineParser parser = new DefaultParser();
		final HelpFormatter formatter = new HelpFormatter();

		try {

			final CommandLine commandLine = parser.parse(options, args);
			return commandLine;
		} catch (ParseException e) {

			System.out.println(String.format("Invalid parameters - %s", e.getMessage()));
			formatter.printHelp("java -jar anchor-[version].jar <options>", options);
			throw e;
		}
	}

	@Override
	public void serviceAdded(final ServiceEvent event) {

		LOG.debug("Network service added: " + event.getInfo());

		signalKClientManager.setEndpointUri(URI.create(event.getInfo().getURLs("ws")[0] + "/v1/stream"));
	}

	@Override
	public void serviceRemoved(final ServiceEvent event) {

		LOG.debug("Network service removed: " + event.getInfo());
	}

	@Override
	public void serviceResolved(final ServiceEvent event) {

		LOG.debug("Network service resolved: " + event.getInfo());
	}
}
