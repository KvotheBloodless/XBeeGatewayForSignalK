package au.com.venilia.xbee_gateway_for_signalk;

import java.io.IOException;
import java.net.URI;

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

import au.com.venilia.xbee_gateway_for_signalk.rxtx.SignalKRXManager;

public class App {

	static {

		// Enable using system proxy if set
		System.setProperty("java.net.useSystemProxies", "true");
	}

	private static final Logger LOG = LoggerFactory.getLogger(App.class);

	private static ApplicationContext context;

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

			context.getBean(SignalKRXManager.class).setEndpointUri(
					URI.create("ws://" + commandLine.getOptionValue("signalk") + "/signalk/v1/stream?subscribe=none"));
		} catch (final ParseException parseException) {

			System.exit(-1);
		}
	}

	private static CommandLine parseCliArguments(String... args) throws ParseException {

		final Options options = new Options();

		final Option a = new Option("k", "signalk", true, "SignalK host:port");
		a.setRequired(true);
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
}
