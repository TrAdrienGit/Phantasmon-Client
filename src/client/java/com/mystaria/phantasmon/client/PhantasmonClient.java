package com.mystaria.phantasmon.client;

import java.net.URI;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import com.mystaria.phantasmon.client.network.BackendHealthPinger;

public class PhantasmonClient implements ClientModInitializer {

	private static final URI HEALTH_URI = URI.create("http://localhost:8080/health");

	private final BackendHealthPinger healthPinger = new BackendHealthPinger(HEALTH_URI);

	@Override
	public void onInitializeClient() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> healthPinger.start());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> healthPinger.stop());
	}
}
