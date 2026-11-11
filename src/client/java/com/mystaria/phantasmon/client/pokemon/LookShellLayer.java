package com.mystaria.phantasmon.client.pokemon;

import com.cobblemon.mod.common.client.render.models.blockbench.pokemon.PosablePokemonEntityModel;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;

/**
 * The glowing shell of a Pokémon's look in the world ({@link LookShells}): added to Cobblemon's Pokémon renderer, it
 * draws the posed model once more, a little bigger, in the shell's moving additive light — as Minecraft draws a
 * charged creeper. Only Phantasmon's own Pokémon carry a look's aspects.
 *
 * <p>Cobblemon's model draw leaves two traces this layer has to deal with: it resets the pose right after drawing (kept
 * for a shell by {@code PosableEntityModelLookMixin}, reset by this layer's own draw), and it shifts the pose stack
 * 1.5 down without restoring it (undone here, else the shell sits 1.5 too low).
 */
public final class LookShellLayer extends RenderLayer<PokemonEntity, PosablePokemonEntityModel> {

	/** Cobblemon's shift in {@code PosableEntityModel.renderToBuffer}, left on the pose stack (1.8.1). */
	private static final float COBBLEMON_SHIFT = 1.5f;
	private static boolean drawing;

	public LookShellLayer(RenderLayerParent<PokemonEntity, PosablePokemonEntityModel> parent) {
		super(parent);
	}

	/** Whether the shell's own draw is under way (its pose reset must then happen). Render thread. */
	public static boolean drawing() {
		return drawing;
	}

	/** Whether {@code entity} will get a shell from this layer, so its pose must outlive the main draw. */
	public static boolean wantsShell(Entity entity) {
		return entity instanceof PokemonEntity pokemon && !pokemon.isInvisible()
				&& LookShells.of(PokemonLook.fromAspects(pokemon.getAspects())) != null;
	}

	@Override
	public void render(PoseStack poseStack, MultiBufferSource buffers, int packedLight, PokemonEntity entity, float limbSwing,
			float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (!wantsShell(entity)) {
			return;
		}
		LookShells.Shell shell = LookShells.of(PokemonLook.fromAspects(entity.getAspects()));
		// Grown around the body's middle (the model's space runs downward from 1.501 above the feet).
		float scale = Math.max(0.01f, entity.getScale());
		float middle = 1.501f - entity.getBbHeight() / scale / 2f;
		poseStack.pushPose();
		poseStack.translate(0, -COBBLEMON_SHIFT, 0);
		poseStack.translate(0, middle, 0);
		poseStack.scale(shell.inflate(), shell.inflate(), shell.inflate());
		poseStack.translate(0, -middle, 0);
		drawing = true;
		try {
			getParentModel().renderToBuffer(poseStack, buffers.getBuffer(shell.type()), packedLight, OverlayTexture.NO_OVERLAY, shell.color());
		} finally {
			drawing = false;
			poseStack.popPose();
		}
	}
}
