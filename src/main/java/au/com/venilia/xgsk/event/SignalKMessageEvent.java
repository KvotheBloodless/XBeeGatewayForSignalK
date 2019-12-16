package au.com.venilia.xgsk.event;

import org.springframework.context.ApplicationEvent;

import com.fasterxml.jackson.databind.JsonNode;

public class SignalKMessageEvent extends ApplicationEvent {

	private final String nodeId;
	private final JsonNode data;

	public SignalKMessageEvent(final String nodeId, final JsonNode data) {

		super(nodeId);

		this.nodeId = nodeId;
		this.data = data;
	}

	public String getNodeId() {

		return nodeId;
	}

	public JsonNode getData() {

		return data;
	}
}
