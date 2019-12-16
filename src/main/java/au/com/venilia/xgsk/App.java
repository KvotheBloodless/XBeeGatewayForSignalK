package au.com.venilia.xgsk;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class App {

	static {

		// Enable using system proxy if set
		System.setProperty("java.net.useSystemProxies", "true");
	}

	private static final Logger LOG = LoggerFactory.getLogger(App.class);

	public App(/* include parameters as required */) {

	}

	public static void main(String... args) throws IOException {

		try (final AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext(
				"au.com.venilia.xgsk")) {

			System.out.println("=========================");
			System.out.println("= XBeeGatewayForSignalK =");
			System.out.println("=========================");
		}
	}
}
