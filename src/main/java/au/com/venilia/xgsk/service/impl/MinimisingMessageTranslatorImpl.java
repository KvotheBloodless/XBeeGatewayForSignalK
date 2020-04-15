package au.com.venilia.xgsk.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import au.com.venilia.xgsk.service.MessageTranslator;

public class MinimisingMessageTranslatorImpl implements MessageTranslator {

	private final ObjectMapper objectMapper;

	public MinimisingMessageTranslatorImpl(final ObjectMapper objectMapper) {

		this.objectMapper = objectMapper;
	}

	@Override
	public byte[] deflate(final JsonNode data) {

		return null;
	}

	@Override
	public JsonNode inflate(final byte[] data) {

		return null;
	}
}
