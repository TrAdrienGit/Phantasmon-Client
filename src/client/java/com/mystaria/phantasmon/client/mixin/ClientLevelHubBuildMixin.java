package com.mystaria.phantasmon.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import com.mystaria.phantasmon.client.hub.HubBuilds;

/**
 * Hub builds (D-34): the Minecraft server knows nothing of the client-only blocks of a Hub build, so a block update it
 * sends for one of those positions ({@code ClientboundBlockUpdatePacket}, {@code ClientboundSectionBlocksUpdatePacket},
 * both through {@code setServerVerifiedBlockState}) would erase it. {@link HubBuilds} keeps the server's state aside
 * (given back when the anchor is deleted) and the build stays.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelHubBuildMixin {

	@Inject(method = "setServerVerifiedBlockState", at = @At("HEAD"), cancellable = true)
	private void phantasmon$keepHubBuild(BlockPos pos, BlockState state, int flags, CallbackInfo ci) {
		if (HubBuilds.interceptServerBlock((ClientLevel) (Object) this, pos, state)) {
			ci.cancel();
		}
	}
}
