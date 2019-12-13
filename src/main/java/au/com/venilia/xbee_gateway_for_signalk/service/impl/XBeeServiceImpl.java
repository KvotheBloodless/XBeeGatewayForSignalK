package au.com.venilia.xbee_gateway_for_signalk.service.impl;

import java.util.Arrays;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;

import au.com.venilia.xbee_gateway_for_signalk.service.XBeeService;

@Service
public class XBeeServiceImpl implements XBeeService, SerialPortDataListener {

	private static final Logger LOG = LoggerFactory.getLogger(XBeeServiceImpl.class);

	private SerialPort xBeePort;

	private final static String REQUEST_SWITCH_STATUS_UPDATE_COMMAND = "?";

	@Autowired
	private ApplicationEventPublisher publisher;

	@Value("${xbee.portDescriptor}")
	private String portDescriptor;

	@Value("${xbee.baudRate:9600}")
	private int baudRate;

	@PostConstruct
	private boolean init() {

		xBeePort = SerialPort.getCommPort(portDescriptor);
		xBeePort.setBaudRate(baudRate);

		if (!xBeePort.openPort()) {

			LOG.error("Could not open connection to xBee on {} with baud rate {}", portDescriptor, baudRate);
			LOG.info("Available ports are:\n{}",
					StringUtils.collectionToDelimitedString(Arrays.asList(SerialPort.getCommPorts()).stream()
							.map(p -> String.format("%s <%s>", p.getPortDescription(), p.getDescriptivePortName()))
							.collect(Collectors.toList()), "\n"));

			return false;
		} else {

			LOG.info("xBee port is open - {}", xBeePort.getDescriptivePortName());
		}

		xBeePort.addDataListener(this);
		return true;
	}

	@Override
	public void write(final byte[] buf) {

		if (!xBeePort.isOpen())
			init();

		xBeePort.writeBytes(buf, buf.length);
		LOG.debug("Wrote {} bytes: [{}]", buf.length, new String(buf));
	}

	@Override
	public int getListeningEvents() {

		return SerialPort.LISTENING_EVENT_DATA_RECEIVED;
	}

	@Override
	public void serialEvent(final SerialPortEvent event) {

		final byte[] data = event.getReceivedData();
		LOG.info("Received {} bytes from xBee: {}", data.length, new String(data));

		// TODO
	}
}
