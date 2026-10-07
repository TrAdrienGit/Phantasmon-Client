package com.mystaria.phantasmon.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.net.NetworkPacket;

import com.mystaria.phantasmon.client.battle.GhostBattles;

/**
 * Spectators of a Ghost battle (Adrien 2026-10-07, "comme dans Cobblemon"): Cobblemon's engine already sends every
 * public update of a battle to its spectators through {@code sendSpectatorUpdate}, as Minecraft packets to the
 * spectating server players. On the host client there is no such player — the engine runs without a server — so the
 * same packets are handed to {@link GhostBattles#onSpectatorUpdate}, which relays them through the backend to the
 * Phantasmon spectators of that battle. The original call still runs (and finds nobody).
 *
 * <p>Targets Cobblemon internals ({@code PokemonBattle.sendSpectatorUpdate}, 1.8.1): a rename fails the game at
 * startup ({@code defaultRequire = 1}) — revalidate after every Cobblemon bump.
 */
@Mixin(value = PokemonBattle.class, remap = false)
public abstract class PokemonBattleSpectatorMixin {

	@Inject(method = "sendSpectatorUpdate", at = @At("HEAD"))
	private void phantasmon$relayToSpectators(NetworkPacket<?> packet, CallbackInfo ci) {
		GhostBattles.onSpectatorUpdate((PokemonBattle) (Object) this, packet);
	}
}
