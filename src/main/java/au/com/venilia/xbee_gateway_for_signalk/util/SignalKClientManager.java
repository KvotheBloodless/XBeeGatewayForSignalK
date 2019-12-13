package au.com.venilia.xbee_gateway_for_signalk.util;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;

import javax.annotation.PreDestroy;
import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.WebSocketContainer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.com.venilia.xbee_gateway_for_signalk.util.SignalKClient.SignalKPath;

public class SignalKClientManager {

    private static final Logger LOG = LoggerFactory.getLogger(SignalKClientManager.class);

    private SignalKClient client;
    private long connectDelay;

    private Thread maintainConnectionThread;
    private boolean maintainConnection = true;

    private boolean keepTrying = true;

    private URI endpointUri;

    public SignalKClientManager(final SignalKClient client, final long connectDelay) {

        this.client = client;
        this.connectDelay = connectDelay;
    }

    public void setEndpointUri(final URI endpointUri) {

        this.endpointUri = endpointUri;
        openConnection();
    }

    private void openConnection() {

        if (maintainConnection) {

            maintainConnectionThread = new Thread(new Runnable() {

                @Override
                public void run() {

                    keepTrying = true;

                    while (keepTrying)

                        try {

                            Thread.sleep(connectDelay);

                            LOG.info("Opening websocket to SignalK server at {}", endpointUri);

                            final WebSocketContainer container = ContainerProvider.getWebSocketContainer();
                            container.connectToServer(client, endpointUri);

                            LOG.info("Successfully opened websocket to SignalK server at {}", endpointUri);

                            for (final SignalKPath value : SignalKPath.values())
                                if (value.subscribe())
                                    client.subscribe(value, Optional.empty(), value.getMinPeriod());

                            keepTrying = false;
                        } catch (final DeploymentException | IOException e) {

                            LOG.warn("{} was thrown attempting to open SignalK socket", e.getClass().getSimpleName());
                            // e.printStackTrace();
                        } catch (final InterruptedException e) {

                            keepTrying = false;
                            LOG.info("Thread to open SignalK socket has been interrupted");
                        }
                }
            });
            maintainConnectionThread.start();
        }
    }

    @PreDestroy
    public void destroy() {

        LOG.info(String.format("Shutting down %s", this.getClass().getSimpleName()));
        maintainConnection = false;

        keepTrying = false;

        if (maintainConnectionThread != null && maintainConnectionThread.isAlive())
            maintainConnectionThread.interrupt();
    }
}
