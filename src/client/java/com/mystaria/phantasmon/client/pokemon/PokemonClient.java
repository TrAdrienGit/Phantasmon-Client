package com.mystaria.phantasmon.client.pokemon;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.mystaria.phantasmon.client.network.BackendConfig;
import com.mystaria.phantasmon.client.network.BackendJsonClient;

/** Thin REST wrapper for the backend's {@code pokemon} domain — every call requires the caller's JWT. */
public final class PokemonClient {

	private final BackendJsonClient httpClient;

	public PokemonClient(BackendJsonClient httpClient) {
		this.httpClient = httpClient;
	}

	public CompletableFuture<PokemonDto> create(String bearerToken, PokemonCreateRequestDto request) {
		return httpClient.post(BackendConfig.BASE_URL.resolve("/pokemon"), request, bearerToken, PokemonDto.class);
	}

	/** Admin (TODO-25): creates in {@code ownerUuid}'s PC — the backend refuses it to non-admins. */
	public CompletableFuture<PokemonDto> createFor(String bearerToken, PokemonCreateRequestDto request, UUID ownerUuid) {
		return httpClient.post(BackendConfig.BASE_URL.resolve("/pokemon?owner=" + ownerUuid), request, bearerToken, PokemonDto.class);
	}

	public CompletableFuture<PokemonDto[]> listForOwner(String bearerToken, UUID ownerUuid) {
		return httpClient.get(BackendConfig.BASE_URL.resolve("/players/" + ownerUuid + "/pokemon"), bearerToken, PokemonDto[].class);
	}

	public CompletableFuture<PokemonDto[]> pcBox(String bearerToken, UUID ownerUuid, int box) {
		return httpClient.get(BackendConfig.BASE_URL.resolve("/players/" + ownerUuid + "/pc?box=" + box), bearerToken, PokemonDto[].class);
	}

	public CompletableFuture<PokemonDto> update(String bearerToken, UUID pokemonUuid, PokemonUpdateRequestDto request) {
		return httpClient.patch(BackendConfig.BASE_URL.resolve("/pokemon/" + pokemonUuid), request, bearerToken, PokemonDto.class);
	}

	public CompletableFuture<Void> delete(String bearerToken, UUID pokemonUuid) {
		return httpClient.delete(BackendConfig.BASE_URL.resolve("/pokemon/" + pokemonUuid), bearerToken);
	}

	/** The battle formats a player may pick (2026-11-28: a PC team row's format), the backend's raw entries. */
	public CompletableFuture<Object[]> battleFormats(String bearerToken) {
		return httpClient.get(BackendConfig.BASE_URL.resolve("/battles/formats"), bearerToken, Object[].class);
	}

	/** A PC team row checked against a format: per entry of {@code pokemon} (null = empty case), the rules it breaks. */
	public CompletableFuture<com.google.gson.JsonObject> validateTeam(String bearerToken, String formatId, java.util.List<UUID> pokemon) {
		return httpClient.post(BackendConfig.BASE_URL.resolve("/battles/validate"), new ValidateRequest(formatId, pokemon), bearerToken,
				com.google.gson.JsonObject.class);
	}

	private record ValidateRequest(String formatId, java.util.List<UUID> pokemon) {
	}

	public CompletableFuture<PokemonDto> clone(String bearerToken, UUID pokemonUuid) {
		return httpClient.postNoBody(BackendConfig.BASE_URL.resolve("/pokemon/" + pokemonUuid + "/clone"), bearerToken, PokemonDto.class);
	}
}
