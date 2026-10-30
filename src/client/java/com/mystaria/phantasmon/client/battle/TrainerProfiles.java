package com.mystaria.phantasmon.client.battle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.Minecraft;

import com.mystaria.phantasmon.client.api.PhantasmonApi;
import com.mystaria.spectacle.api.SpectacleHooks;

/**
 * The trainers' animation profiles shared through the backend (Cobblemon Spectacle phase E3, Adrien 2026-10-30): this
 * player's own, chosen in Spectacle's profile screen, is sent on login and on every change ({@code POST
 * /trainer-profile}); the other trainers' are fetched ({@code GET /trainer-profiles}) as a battle starts and whenever
 * Spectacle asks for one it doesn't know, kept a minute. Until fetched, a trainer acts the default animations.
 * Client thread.
 */
public final class TrainerProfiles {

	private static final Logger LOG = LoggerFactory.getLogger(TrainerProfiles.class);
	private static final long KEEP_MILLIS = 60_000;

	private record Known(Map<String, String> profile, long at) {
	}

	private static final Map<UUID, Known> KNOWN = new HashMap<>();
	private static final Set<UUID> FETCHING = new HashSet<>();
	/** The own profile still has to be sent (logged in again, or changed while logged out). */
	private static boolean sendOwn = true;
	private static boolean wasAuthenticated;

	private TrainerProfiles() {
	}

	public static void register() {
		SpectacleHooks.setTrainerProfiles(TrainerProfiles::animation);
		SpectacleHooks.setTrainersListener(TrainerProfiles::fetch);
		SpectacleHooks.setLocalProfileListener(profile -> {
			sendOwn = true;
			send();
		});
	}

	/** Every client tick: the own profile sent after logging in. */
	public static void tick() {
		boolean authenticated = PhantasmonApi.isAuthenticated();
		if (authenticated && !wasAuthenticated) {
			sendOwn = true;
		}
		wasAuthenticated = authenticated;
		if (sendOwn && authenticated) {
			send();
		}
	}

	private static void send() {
		if (!PhantasmonApi.isAuthenticated()) {
			return; // sent once logged in
		}
		sendOwn = false;
		PhantasmonApi.post("/trainer-profile", SpectacleHooks.localTrainerProfile(), Map.class)
				.exceptionally(ex -> {
					LOG.warn("Could not send the trainer animation profile", ex);
					Minecraft.getInstance().execute(() -> sendOwn = true);
					return null;
				});
	}

	/** Spectacle asks: {@code trainer}'s animation for {@code moment}, or null (default) until known. */
	private static String animation(UUID trainer, String moment) {
		Known known = KNOWN.get(trainer);
		if (known == null || System.currentTimeMillis() - known.at() > KEEP_MILLIS) {
			fetch(List.of(trainer));
		}
		return known == null ? null : known.profile().get(moment);
	}

	private static void fetch(Collection<UUID> trainers) {
		if (!PhantasmonApi.isAuthenticated()) {
			return;
		}
		List<UUID> wanted = new ArrayList<>();
		for (UUID uuid : trainers) {
			if (FETCHING.add(uuid)) {
				wanted.add(uuid);
			}
		}
		if (wanted.isEmpty()) {
			return;
		}
		String players = wanted.stream().map(UUID::toString).collect(Collectors.joining(","));
		PhantasmonApi.get("/trainer-profiles?players=" + players, Map.class).whenComplete((result, error) ->
				Minecraft.getInstance().execute(() -> {
					wanted.forEach(FETCHING::remove);
					long now = System.currentTimeMillis();
					for (UUID uuid : wanted) {
						Map<String, String> profile = new HashMap<>();
						if (error == null && result != null && result.get(uuid.toString()) instanceof Map<?, ?> found) {
							found.forEach((moment, name) -> profile.put(String.valueOf(moment), String.valueOf(name)));
						}
						// A failure counts as "nothing chosen" for a minute: no request on every frame.
						KNOWN.put(uuid, new Known(profile, now));
					}
				}));
	}
}
