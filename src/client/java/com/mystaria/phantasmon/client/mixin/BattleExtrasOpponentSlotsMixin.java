package com.mystaria.phantasmon.client.mixin;

import java.lang.reflect.Method;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mystaria.phantasmon.client.compat.BattleExtrasCompat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Cobblemon Battle Extras' opponent column (optional, Adrien 2026-11-08): in a Phantasmon battle the Poké Ball behind
 * each portrait isn't drawn, and a K.O. Pokémon's portrait turns red (2026-11-25). Intermediary names, not remapped. Render thread only.
 */
@Pseudo
@Mixin(targets = "name.modid.client.OpponentInfoSideRenderer", remap = false)
public abstract class BattleExtrasOpponentSlotsMixin {

	@Unique
	private static boolean phantasmon$hideBall;

	@Inject(method = "renderSlotForeground", at = @At("HEAD"), require = 0)
	private static void phantasmon$slot(GuiGraphics g, @Coerce Object slot, int x, int y, int size, float alpha, CallbackInfo ci) {
		phantasmon$hideBall = BattleExtrasCompat.portraitsOnly();
		BattleExtrasCompat.redPortrait(phantasmon$hideBall && BattleExtrasCompat.fainted(slot));
	}

	@Inject(method = "renderSlotForeground", at = @At("RETURN"), require = 0)
	private static void phantasmon$slotDone(CallbackInfo ci) {
		BattleExtrasCompat.redPortrait(false);
	}

	@WrapWithCondition(method = "renderSlotForeground", require = 0,
			at = @At(value = "INVOKE", target = "Lnet/minecraft/class_332;method_25293(Lnet/minecraft/class_2960;IIIIFFIIII)V"))
	private static boolean phantasmon$ball(GuiGraphics g, ResourceLocation texture, int x, int y, int w, int h, float u, float v,
			int uw, int vh, int tw, int th) {
		return !phantasmon$hideBall;
	}

	/** Cobblemon's portrait call (by reflection): a K.O. Pokémon's portrait is tinted red. */
	@WrapOperation(method = "invokeDrawPosablePortrait", require = 0,
			at = @At(value = "INVOKE", target = "Ljava/lang/reflect/Method;invoke(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;"))
	private static Object phantasmon$redPortrait(Method method, Object target, Object[] args, Operation<Object> original) {
		BattleExtrasCompat.tint(args);
		return original.call(method, target, args);
	}
}
