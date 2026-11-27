package com.mystaria.phantasmon.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cobblemon.mod.common.pokemon.Pokemon;

import net.minecraft.world.item.ItemStack;

import com.mystaria.phantasmon.client.battle.GhostBattlePokemonFactory;

/**
 * A hosted battle runs Cobblemon's engine on the client. When a battle copy loses or gains an item there (a berry
 * eaten, Knock Off, Trick), Cobblemon calls {@code swapHeldItem}, which posts {@code HeldItemEvent}; its MoLang
 * context calls {@code server()!!} and threw on a client connected to a dedicated server, ending the battle as a draw
 * (Sitrus Berry eaten after an Earthquake, 2026-11-27). A throwaway battle copy needs neither the event, nor the
 * update packet, nor the stash hand-back: its item is set directly.
 *
 * <p>Targets a Cobblemon method ({@code swapHeldItem}, 1.8.1): revalidate after every Cobblemon bump.
 */
@Mixin(value = Pokemon.class, remap = false)
public abstract class PokemonHeldItemSwapMixin {

	@Inject(method = "swapHeldItem", at = @At("HEAD"), cancellable = true)
	private void phantasmon$battleCopySwapsSilently(ItemStack stack, boolean decrement, boolean visible,
			CallbackInfoReturnable<ItemStack> cir) {
		Pokemon pokemon = (Pokemon) (Object) this;
		if (GhostBattlePokemonFactory.isBattleCopy(pokemon)) {
			ItemStack returning = pokemon.heldItem();
			ItemStack receiving = stack.copy();
			receiving.setCount(Math.min(receiving.getCount(), 1));
			pokemon.setHeldItem$common(receiving);
			cir.setReturnValue(returning);
		}
	}
}
