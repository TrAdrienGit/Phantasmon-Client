package com.mystaria.phantasmon.client.network;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Confirms Gson (as resolved on this project's classpath) serializes
 * {@link UUID} fields as a plain string, not its private
 * {@code mostSigBits}/{@code leastSigBits} fields — load-bearing for every
 * DTO with a UUID field ({@code AuthSessionRequestDto.uuid}, every
 * {@code *RequestDto.requestUuid}, {@code PokemonCreateRequestDto} fields...).
 * If this ever fails after a Gson version bump, every one of those requests
 * would silently send garbage instead of a UUID string.
 */
class GsonUuidSanityTest {

	private record Sample(UUID requestUuid) {
	}

	@Test
	void uuidSerializesAsPlainString() {
		Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
		UUID uuid = UUID.randomUUID();

		String json = gson.toJson(new Sample(uuid));

		assertEquals("{\"request_uuid\":\"" + uuid + "\"}", json);
	}
}
