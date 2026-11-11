package com.mystaria.phantasmon.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.cobblemon.mod.common.client.render.models.blockbench.PosableEntityModel;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext;

import net.minecraft.world.entity.Entity;

import com.mystaria.phantasmon.client.pokemon.LookShellLayer;

/**
 * Looks (TODO-37): Cobblemon puts its model back in its rest pose as soon as it has drawn it, before the renderer's
 * layers run. For a Pokémon wearing a glowing shell the pose is kept until {@link LookShellLayer} has drawn the shell
 * over it (that second draw resets it as usual), so the shell moves with the body. Cobblemon 1.8.1: revalidate after
 * every Cobblemon bump.
 */
@Mixin(PosableEntityModel.class)
public abstract class PosableEntityModelLookMixin {

	@Redirect(method = "renderToBuffer",
			at = @At(value = "INVOKE",
					target = "Lcom/cobblemon/mod/common/client/render/models/blockbench/PosableModel;setDefault()V",
					remap = false))
	private void phantasmon$keepPoseForShell(PosableModel model) {
		Entity entity = ((PosableEntityModel<?>) (Object) this).getContext().request(RenderContext.Companion.getENTITY());
		if (LookShellLayer.drawing() || !LookShellLayer.wantsShell(entity)) {
			model.setDefault();
		}
	}
}
