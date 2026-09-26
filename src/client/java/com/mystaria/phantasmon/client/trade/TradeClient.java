package com.mystaria.phantasmon.client.trade;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.mystaria.phantasmon.client.network.BackendConfig;
import com.mystaria.phantasmon.client.network.BackendJsonClient;

/** Thin REST wrapper for the backend's {@code trade} domain — every call requires the caller's JWT. */
public final class TradeClient {

	private final BackendJsonClient httpClient;

	public TradeClient(BackendJsonClient httpClient) {
		this.httpClient = httpClient;
	}

	public CompletableFuture<TradeDto> propose(String bearerToken, ProposeTradeRequestDto request) {
		return httpClient.post(BackendConfig.BASE_URL.resolve("/trades"), request, bearerToken, TradeDto.class);
	}

	public CompletableFuture<TradeDto> accept(String bearerToken, UUID tradeUuid) {
		return httpClient.postNoBody(BackendConfig.BASE_URL.resolve("/trades/" + tradeUuid + "/accept"), bearerToken, TradeDto.class);
	}

	public CompletableFuture<TradeDto> cancel(String bearerToken, UUID tradeUuid) {
		return httpClient.postNoBody(BackendConfig.BASE_URL.resolve("/trades/" + tradeUuid + "/cancel"), bearerToken, TradeDto.class);
	}

	public CompletableFuture<TradeDto> get(String bearerToken, UUID tradeUuid) {
		return httpClient.get(BackendConfig.BASE_URL.resolve("/trades/" + tradeUuid), bearerToken, TradeDto.class);
	}

	public CompletableFuture<TradeDto[]> listForPlayer(String bearerToken, UUID playerUuid) {
		return httpClient.get(BackendConfig.BASE_URL.resolve("/players/" + playerUuid + "/trades"), bearerToken, TradeDto[].class);
	}
}
