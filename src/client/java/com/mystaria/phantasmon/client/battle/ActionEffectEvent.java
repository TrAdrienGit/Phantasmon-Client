package com.mystaria.phantasmon.client.battle;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.google.gson.Gson;

/**
 * One Cobblemon action effect (move animation, stat boost, status, damage...) to play on both clients of a Ghost
 * battle. Built by the host's engine ({@link GhostActionEffects}); played by {@link ActionEffectPlayer}.
 *
 * <p>Battle positions are Showdown "pnx" strings ({@code p1a}, {@code p2a}...), which each client maps to its
 * own client-side Pokémon entities ({@link BattleVisuals#entityAt}). {@code missed}/{@code failed}/{@code hurt}
 * mirror the {@code q.missed(...)}, {@code q.failed(...)}, {@code q.hurt(...)} MoLang queries of Cobblemon's
 * {@code MoveInstruction}, which the action effect JSON files rely on.
 */
public record ActionEffectEvent(
		String effectId,
		List<String> users,
		List<String> targets,
		List<String> missed,
		List<String> failed,
		boolean anyFailed,
		List<String> hurt,
		Integer hitCount,
		String moveName,
		String instructionId) {

	/** Relayed to the guest inside a {@code BattlePacket}, next to the real Cobblemon packets. */
	public static final String PACKET_ID = "phantasmon:action_effect";

	private static final Gson GSON = new Gson();

	public byte[] toBytes() {
		return GSON.toJson(this).getBytes(StandardCharsets.UTF_8);
	}

	public static ActionEffectEvent fromBytes(byte[] bytes) {
		return GSON.fromJson(new String(bytes, StandardCharsets.UTF_8), ActionEffectEvent.class);
	}
}
