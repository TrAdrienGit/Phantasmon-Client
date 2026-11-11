package com.mystaria.phantasmon.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import com.mystaria.phantasmon.client.pokemon.LookTextures;

/**
 * Looks (TODO-37): a holographic Pokémon is truly see-through — its recoloured texture is drawn translucent instead of
 * cut out; and while a menu draws a Pokémon's glowing shell ({@link LookTextures#guiShell}), its model is drawn in
 * the shell's additive light. Everything else is left alone.
 */
@Mixin(RenderType.class)
public abstract class RenderTypeLookMixin {

	@Inject(method = "entityCutout", at = @At("HEAD"), cancellable = true)
	private static void phantasmon$lookCutout(ResourceLocation texture, CallbackInfoReturnable<RenderType> cir) {
		RenderType look = LookTextures.renderType(texture);
		if (look != null) {
			cir.setReturnValue(look);
		}
	}

	@Inject(method = "entityCutoutNoCull(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/RenderType;",
			at = @At("HEAD"), cancellable = true)
	private static void phantasmon$lookCutoutNoCull(ResourceLocation texture, CallbackInfoReturnable<RenderType> cir) {
		RenderType look = LookTextures.renderType(texture);
		if (look != null) {
			cir.setReturnValue(look);
		}
	}
}
