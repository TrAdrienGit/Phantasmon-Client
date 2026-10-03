package com.mystaria.phantasmon.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cobblemon.mod.common.client.gui.interact.wheel.InteractWheelGUI;
import com.cobblemon.mod.common.client.gui.interact.wheel.InteractWheelGuiFactoryKt;
import com.cobblemon.mod.common.net.messages.client.PlayerInteractOptionsPacket;

import com.mystaria.phantasmon.client.wheel.GhostWheelOptions;

/**
 * Adds "Ghost Trade" and "Ghost Battle" to the wheel Cobblemon opens when you press R on another player
 * (Adrien, 2026-10-03). Cobblemon builds that wheel from a closed enum filled in by its server, with no
 * extension point, so the finished screen is topped up here instead — see {@link GhostWheelOptions}.
 *
 * <p>Targets Cobblemon internals ({@code createPlayerInteractGui}, 1.8.1): if a Cobblemon update renames or
 * reshapes it, the game fails at startup with a clear mixin error rather than silently misbehaving
 * ({@code defaultRequire = 1} in the mixin config) — the thing to revalidate after every Cobblemon bump.
 */
@Mixin(value = InteractWheelGuiFactoryKt.class, remap = false)
public abstract class InteractWheelGuiFactoryMixin {

	@Inject(method = "createPlayerInteractGui", at = @At("RETURN"))
	private static void phantasmon$addGhostOptions(PlayerInteractOptionsPacket optionsPacket,
			CallbackInfoReturnable<InteractWheelGUI> cir) {
		GhostWheelOptions.addTo(((InteractWheelGuiAccessor) (Object) cir.getReturnValue()).phantasmon$getOptions(), optionsPacket);
	}
}
