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

	public CompletableFuture<PokemonDto> clone(String bearerToken, UUID pokemonUuid) {
		return httpClient.postNoBody(BackendConfig.BASE_URL.resolve("/pokemon/" + pokemonUuid + "/clone"), bearerToken, PokemonDto.class);
	}
}
