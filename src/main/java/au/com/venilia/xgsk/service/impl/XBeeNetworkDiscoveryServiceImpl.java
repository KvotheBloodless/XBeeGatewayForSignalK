package au.com.venilia.xgsk.service.impl;

import java.io.IOException;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.PreDestroy;
import javax.websocket.DeploymentException;

import org.apache.commons.lang3.tuple.Triple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import com.digi.xbee.api.RemoteXBeeDevice;
import com.digi.xbee.api.XBeeDevice;
import com.digi.xbee.api.XBeeNetwork;
import com.digi.xbee.api.exceptions.XBeeException;
import com.digi.xbee.api.listeners.IDiscoveryListener;
import com.google.common.collect.Maps;

import au.com.venilia.xgsk.client.SignalKClient;
import au.com.venilia.xgsk.client.SignalKClient.SignalKClientFactory;
import au.com.venilia.xgsk.client.XBeeClient;
import au.com.venilia.xgsk.client.XBeeClient.XBeeClientFactory;
import au.com.venilia.xgsk.service.XBeeNetworkDiscoveryService;

@Service
public class XBeeNetworkDiscoveryServiceImpl implements XBeeNetworkDiscoveryService {

	private static final Logger LOG = LoggerFactory.getLogger(XBeeNetworkDiscoveryServiceImpl.class);

	@Autowired
	@Lazy
	private SignalKClientFactory signalKClientFactory;

	@Autowired
	@Lazy
	private XBeeClientFactory xBeeClientFactory;

	@Autowired
	private TaskScheduler scheduler;

	@Value("${xbee.discovery.interval:10}")
	private long discoveryRunDelaySeconds;

	private XBeeDevice localInstance;
	private XBeeNetwork network;

	private final Map<String, Triple<RemoteXBeeDevice, SignalKClient, XBeeClient>> peers;

	@Autowired
	public XBeeNetworkDiscoveryServiceImpl(@Value("${xbee.portDescriptor}") final String serialDescriptor,
			@Value("${xbee.baudRate}") final int baudRate) {

		LOG.info("Creating network discovery service for serial port {} and baud rate {}", serialDescriptor, baudRate);

		localInstance = new XBeeDevice(serialDescriptor, baudRate);

		peers = Maps.newHashMap();

		init();
	}

	private void init() {

		try {

			localInstance.open();

			network = localInstance.getNetwork();

			scheduler.scheduleWithFixedDelay(new Runnable() {

				@Override
				public void run() {

					try {

						performDiscovery();
					} catch (final XBeeException e) {

						LOG.error("A {} was thrown during discovery - {}", e.getClass().getSimpleName(), e.getMessage(),
								e);
					}
				}
			}, Duration.ofSeconds(discoveryRunDelaySeconds));
		} catch (final XBeeException e) {

			LOG.error("An {} was thrown opening connection to local module - {}", e.getClass().getSimpleName(),
					e.getMessage(), e);
			// throw new RuntimeException(e);
		}
	}

	private IDiscoveryListener discoveryListener;

	private void performDiscovery() throws XBeeException {

		if (network.isDiscoveryRunning())
			throw new IllegalStateException("XBee module discovery is already in progress");

		LOG.info("Performing XBee module disovery on network {}", network);

		final Map<RemoteXBeeDevice, Boolean> deviceSeenDuringThisDiscovery = network.getDevices().stream()
				.collect(Collectors.toMap(d -> d, d -> false));

		if (discoveryListener != null)
			network.removeDiscoveryListener(discoveryListener);

		discoveryListener = new IDiscoveryListener() {

			@Override
			public void deviceDiscovered(final RemoteXBeeDevice discoveredDevice) {

				if (!deviceSeenDuringThisDiscovery.containsKey(discoveredDevice)) {

					LOG.debug("New module {} found during discovery process; adding to list.", discoveredDevice);

					try {

						peers.put(discoveredDevice.getNodeID(),
								Triple.of(discoveredDevice, signalKClientFactory.client(discoveredDevice.getNodeID()),
										xBeeClientFactory.client(discoveredDevice)));
					} catch (final DeploymentException | IOException e) {

						LOG.error("{} thrown during setup of new bridge", e.getClass().getSimpleName(), e);
					}

				} else {

					LOG.debug("Known module {} seen during discovery process", discoveredDevice);

					deviceSeenDuringThisDiscovery.put(discoveredDevice, true);
				}
			}

			@Override
			public void discoveryFinished(final String error) {

				if (error != null)
					throw new RuntimeException(new XBeeException(
							String.format("An error occurred during remote XBee module discovery - %s", error)));

				deviceSeenDuringThisDiscovery.entrySet().stream().filter(e -> !e.getValue().booleanValue())
						.map(e -> e.getKey()).forEach(d -> {

							LOG.debug("Module {} was not seen during discovery process; removing from list.", d);

							// TODO: Confirm that the clients are destroyed
							peers.remove(d.getNodeID(), d);
							network.removeRemoteDevice(d);
						});

				LOG.debug("Discovery completed. Remote XBee modules: {}", network.getDevices());
			}

			@Override
			public void discoveryError(final String error) {

				throw new RuntimeException(new XBeeException(
						String.format("An error occurred during remote XBee module discovery - %s", error)));
			}
		};

		network.addDiscoveryListener(discoveryListener);

		// Start the discovery process
		network.startDiscoveryProcess();
	}

	@Override
	public XBeeDevice getLocalInstance() {

		return localInstance;
	}

	@Override
	public void evictNode(String nodeID) {

		peers.remove(nodeID);
	}

	@PreDestroy
	private void shutdown() {

		final Iterator<Triple<RemoteXBeeDevice, SignalKClient, XBeeClient>> peerIterator = peers.values().iterator();
		while (peerIterator.hasNext()) {

			final Triple<RemoteXBeeDevice, SignalKClient, XBeeClient> peer = peerIterator.next();

			// TODO: Test if we need to call these
			// peer.getMiddle().destroy();
			// peer.getRight().destroy();

			peerIterator.remove();
		}

		if (localInstance.isOpen())
			localInstance.close();
	}
}
