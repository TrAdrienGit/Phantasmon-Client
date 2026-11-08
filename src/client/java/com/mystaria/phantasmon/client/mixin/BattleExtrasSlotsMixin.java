package com.mystaria.phantasmon.client.mixin;

import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mystaria.phantasmon.client.compat.BattleExtrasCompat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/**
 * Cobblemon Battle Extras' own team column (optional, Adrien 2026-11-08): in a Phantasmon battle every slot shows its
 * Pokémon, not a Poké Ball (only a fainted one keeps its darkened ball), and Phantasmon's virtual items show in the
 * tooltip. Targets are Battle Extras' runtime (intermediary) names: nothing here is remapped.
 */
@Pseudo
@Mixin(targets = "name.modid.client.PartyInfoSideRenderer", remap = false)
public abstract class BattleExtrasSlotsMixin {

	@ModifyVariable(method = "renderPartySlotForeground", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
	private static boolean phantasmon$portrait(boolean portrait, @Local(argsOnly = true) Pokemon pokemon,
			@Local(argsOnly = true, ordinal = 0) int slot, @Local(argsOnly = true) UUID actor) {
		return BattleExtrasCompat.portraitsOnly() ? !BattleExtrasCompat.fainted(actor, slot, pokemon) : portrait;
	}

	@Inject(method = "renderPartySlotBall", at = @At("HEAD"), cancellable = true, require = 0)
	private static void phantasmon$noBall(GuiGraphics g, Pokemon pokemon, int slot, UUID actor, int x, int y, CallbackInfo ci) {
		if (BattleExtrasCompat.portraitsOnly() && !BattleExtrasCompat.fainted(actor, slot, pokemon)) {
			ci.cancel();
		}
	}

	@Inject(method = "safeHeldItem", at = @At("HEAD"), cancellable = true, require = 0)
	private static void phantasmon$virtualItem(Pokemon pokemon, CallbackInfoReturnable<ItemStack> cir) {
		ItemStack stack = BattleExtrasCompat.virtualItem(pokemon);
		if (stack != null) {
			cir.setReturnValue(stack);
		}
	}

	@WrapOperation(method = "renderTooltip", require = 0,
			at = @At(value = "INVOKE", target = "Lnet/minecraft/class_332;method_51427(Lnet/minecraft/class_1799;II)V"))
	private static void phantasmon$virtualIcon(GuiGraphics g, ItemStack stack, int x, int y, Operation<Void> original) {
		if (!BattleExtrasCompat.drawVirtualItem(g, stack, x, y)) {
			original.call(g, stack, x, y);
		}
	}
}
