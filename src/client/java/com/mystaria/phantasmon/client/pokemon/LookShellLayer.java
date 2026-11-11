package com.mystaria.phantasmon.client.pokemon;

import com.cobblemon.mod.common.client.render.models.blockbench.pokemon.PosablePokemonEntityModel;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * The glowing shell of a Pokémon's look in the world ({@link LookShells}): added to Cobblemon's Pokémon renderer, it
 * draws the posed model once more, a little bigger, in the shell's moving additive light — as Minecraft draws a
 * charged creeper. Only Phantasmon's own Pokémon carry a look's aspects.
 */
public final class LookShellLayer extends RenderLayer<PokemonEntity, PosablePokemonEntityModel> {

	public LookShellLayer(RenderLayerParent<PokemonEntity, PosablePokemonEntityModel> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack poseStack, MultiBufferSource buffers, int packedLight, PokemonEntity entity, float limbSwing,
			float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (entity.isInvisible()) {
			return;
		}
		LookShells.Shell shell = LookShells.of(PokemonLook.fromAspects(entity.getAspects()));
		if (shell == null) {
			return;
		}
		// Grown around the body's middle (the model's space runs downward from 1.501 above the feet).
		float scale = Math.max(0.01f, entity.getScale());
		float middle = 1.501f - entity.getBbHeight() / scale / 2f;
		poseStack.pushPose();
		poseStack.translate(0, middle, 0);
		poseStack.scale(shell.inflate(), shell.inflate(), shell.inflate());
		poseStack.translate(0, -middle, 0);
		getParentModel().renderToBuffer(poseStack, buffers.getBuffer(shell.type()), packedLight, OverlayTexture.NO_OVERLAY, shell.color());
		poseStack.popPose();
	}
}
