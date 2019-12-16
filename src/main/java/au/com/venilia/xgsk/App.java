package au.com.venilia.xgsk;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class App {

	private static final Logger LOG = LoggerFactory.getLogger(App.class);

	static {

		// Enable using system proxy if set
		System.setProperty("java.net.useSystemProxies", "true");
	}

	private static volatile boolean run = true;

	public App() {

		Runtime.getRuntime().addShutdownHook(new Thread() {

			public void run() {

				run = false;
			}

		});

		final Thread runThread = new Thread() {

			@Override
			public void run() {

				try {

					while (run)
						Thread.sleep(1000);
				} catch (final Exception ex) {

					System.err.println("Failed to stop App");
				}
			}
		};

		runThread.start();
		try {

			runThread.join();
		} catch (InterruptedException e) {

			System.out.println("Run thread died");
		}
	}

	public static void main(String... args) throws IOException {

		try (final AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext(
				"au.com.venilia.xgsk")) {

			System.out.println("=========================");
			System.out.println("= XBeeGatewayForSignalK =");
			System.out.println("=========================");

			new App();
		}
	}
}
