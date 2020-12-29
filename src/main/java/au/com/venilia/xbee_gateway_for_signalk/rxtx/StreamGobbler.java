package au.com.venilia.xbee_gateway_for_signalk.rxtx;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.function.Consumer;

public class StreamGobbler implements Runnable {

    private InputStream inputStream;
    private Consumer<String> consumer;

    public StreamGobbler(final InputStream inputStream, final Consumer<String> consumer) {

        this.inputStream = inputStream;
        this.consumer = consumer;
    }

    @Override
    public void run() {

        new BufferedReader(new InputStreamReader(inputStream)).lines().forEach(consumer);
    }
}
