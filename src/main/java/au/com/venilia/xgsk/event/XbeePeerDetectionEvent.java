package au.com.venilia.xgsk.event;

import org.springframework.context.ApplicationEvent;

import com.digi.xbee.api.RemoteXBeeDevice;

public class XbeePeerDetectionEvent extends ApplicationEvent {

	private final RemoteXBeeDevice peer;

	public XbeePeerDetectionEvent(final Object source, final RemoteXBeeDevice peer) {

		super(source);

		this.peer = peer;
	}

	public RemoteXBeeDevice getPeer() {

		return peer;
	}
}
