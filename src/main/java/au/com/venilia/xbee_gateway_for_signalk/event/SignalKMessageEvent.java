package au.com.venilia.xbee_gateway_for_signalk.event;

import org.springframework.context.ApplicationEvent;

import com.google.common.base.MoreObjects;

public class SignalKMessageEvent extends ApplicationEvent {

	private final String path;
	private final Object value;

	public SignalKMessageEvent(final String source, final String path, final Object value) {

		super(source);
		this.path = path;
		this.value = value;
	}

	public String getPath() {

		return path;
	}

	public Object getValue() {

		return value;
	}

	@Override
	public String toString() {

		return MoreObjects.toStringHelper(this).add("source", source).add("path", path).add("value", value).toString();
	}
}
