package com.mystaria.phantasmon.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cobblemon.mod.common.net.messages.server.battle.BattleSelectActionsPacket;

import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;

import com.mystaria.phantasmon.client.battle.GhostBattles;

/**
 * The only Mixin of the mod (Phase 9). Cobblemon's battle UI sends the
 * player's choices to the Minecraft server; for a Ghost battle there is no
 * such battle on the server — it runs on a player's client — so the choice
 * is stopped here and routed to the Ghost battle engine instead. Every other
 * packet, Cobblemon's included, goes through untouched. Targets a vanilla
 * method (not Cobblemon internals), which is far less likely to move.
 */
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ClientCommonPacketListenerImplMixin {

	@Inject(method = "send", at = @At("HEAD"), cancellable = true)
	private void phantasmon$routeGhostBattleChoices(Packet<?> packet, CallbackInfo ci) {
		if (packet instanceof ServerboundCustomPayloadPacket custom
				&& custom.payload() instanceof BattleSelectActionsPacket choice
				&& GhostBattles.intercepts(choice)) {
			GhostBattles.onLocalChoice(choice);
			ci.cancel();
		}
	}
}
