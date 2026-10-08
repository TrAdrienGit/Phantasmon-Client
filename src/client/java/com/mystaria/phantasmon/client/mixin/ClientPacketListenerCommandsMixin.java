package com.mystaria.phantasmon.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;

import com.mystaria.phantasmon.client.admin.CommandTreeRefresher;

/**
 * Keeps the last command tree the server sent, so {@link CommandTreeRefresher} can hand it again to
 * {@code handleCommands} — which makes Fabric copy the client commands into the completion tree again, re-checking
 * their {@code requires} (the admin commands, shown only once the backend says the player is an admin). Fabric API
 * 0.116 (1.21.1) has no {@code ClientCommands.refreshCommandCompletions()} yet: this does what it does.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerCommandsMixin {

	@Inject(method = "handleCommands", at = @At("HEAD"))
	private void phantasmon$rememberCommandTree(ClientboundCommandsPacket packet, CallbackInfo ci) {
		CommandTreeRefresher.remember(packet);
	}
}
