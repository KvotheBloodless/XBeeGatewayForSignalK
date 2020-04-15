package au.com.venilia.xgsk.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;

public interface MessageTranslator {

	/**
	 * Take a message from the signalK server, interpret in and translate it into an
	 * xBee message
	 * 
	 * @throws JsonProcessingException
	 * @throws JsonMappingException
	 */
	public byte[] deflate(final JsonNode message) throws JsonMappingException, JsonProcessingException;

	/**
	 * Take a message from the xBee, interpret in and translate it into a signalK
	 * server message
	 */
	public JsonNode inflate(final byte[] message);
}
