package com.mystaria.phantasmon.client.pokemon;

import java.util.Set;

import net.minecraft.client.Minecraft;

import com.mystaria.phantasmon.client.api.PhantasmonApi;
import com.mystaria.showtime.api.ShowtimeHooks;

/**
 * The reserved battle intros and ambiances the local player won (TODO-37): rewards of kind {@code INTRO} or
 * {@code AMBIANCE}, read from {@code /rewards/mine} after each login and when a menu needs them fresh. Showtime asks
 * here ({@link ShowtimeHooks#setUnlocks}) before showing or picking reserved content; an admin sees everything (to
 * preview and give it). Client thread.
 */
public final class RewardContent {

	private static Set<String> intros = Set.of();
	private static Set<String> ambiances = Set.of();

	private RewardContent() {
	}

	public static void register() {
		ShowtimeHooks.setUnlocks(RewardContent::unlocked);
	}

	private static boolean unlocked(String kind, String id) {
		if (PhantasmonApi.isAdmin()) {
			return true;
		}
		return ShowtimeHooks.INTRO.equals(kind) ? intros.contains(id) : ShowtimeHooks.AMBIANCE.equals(kind) && ambiances.contains(id);
	}

	/** Reads the player's rewards again (after a login, before a lobby or the rewards page). */
	public static void refresh() {
		PhantasmonApi.get("/rewards/mine", PokemonLook.Reward[].class)
				.thenAccept(won -> Minecraft.getInstance().execute(() -> {
					java.util.Set<String> wonIntros = new java.util.HashSet<>();
					java.util.Set<String> wonAmbiances = new java.util.HashSet<>();
					for (PokemonLook.Reward reward : won == null ? new PokemonLook.Reward[0] : won) {
						if ("INTRO".equals(reward.kind())) {
							wonIntros.add(reward.value());
						} else if ("AMBIANCE".equals(reward.kind())) {
							wonAmbiances.add(reward.value());
						}
					}
					intros = Set.copyOf(wonIntros);
					ambiances = Set.copyOf(wonAmbiances);
				}))
				.exceptionally(ex -> null);
	}

	/** Logged out: nothing unlocked. */
	public static void clear() {
		intros = Set.of();
		ambiances = Set.of();
	}
}
