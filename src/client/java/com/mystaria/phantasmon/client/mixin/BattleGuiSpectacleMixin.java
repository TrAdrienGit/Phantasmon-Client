package com.mystaria.phantasmon.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cobblemon.mod.common.client.gui.battle.BattleGUI;

import net.minecraft.client.gui.GuiGraphics;

import com.mystaria.phantasmon.client.battle.BattleSpectacle;

/** Cobblemon's battle screen steps aside while a set piece plays ({@link BattleSpectacle}). */
@Mixin(BattleGUI.class)
public abstract class BattleGuiSpectacleMixin {

	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void phantasmon$hideDuringSpectacle(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		if (BattleSpectacle.playing()) {
			ci.cancel();
		}
	}
}
