package com.mystaria.phantasmon.client.mixin;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.phys.Vec3;

import com.mystaria.phantasmon.client.hub.HubAvatarEntity;
import com.mystaria.phantasmon.client.voice.HubVoice;

/**
 * Hub avatars (other servers' players) get Simple Voice Chat's icon left of their name — speaking, whispering, muted,
 * voice off, no voice ({@link HubVoice#iconOf}, Adrien 2026-11-10) — drawn like the name tag itself: same anchor,
 * facing the camera, faint through walls.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererVoiceIconMixin {

	@Shadow
	@Final
	protected EntityRenderDispatcher entityRenderDispatcher;

	@Shadow
	public abstract Font getFont();

	@Inject(method = "renderNameTag", at = @At("TAIL"))
	private void phantasmon$hubVoiceIcon(Entity entity, Component displayName, PoseStack poseStack, MultiBufferSource buffers,
			int packedLight, float partialTick, CallbackInfo ci) {
		if (!(entity instanceof HubAvatarEntity avatar) || entityRenderDispatcher.distanceToSqr(entity) > 4096.0) {
			return;
		}
		// The member's own uuid (the avatar entity has one derived from it).
		ResourceLocation icon = HubVoice.iconOf(avatar.getGameProfile().getId());
		if (icon == null) {
			return;
		}
		Vec3 anchor = entity.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, entity.getViewYRot(partialTick));
		if (anchor == null) {
			return;
		}
		poseStack.pushPose();
		poseStack.translate(anchor.x, anchor.y + 0.5, anchor.z);
		poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
		poseStack.scale(0.025F, -0.025F, 0.025F);
		Matrix4f pose = poseStack.last().pose();
		float right = -getFont().width(displayName) / 2f - 2f;
		float left = right - 10f;
		if (!entity.isDiscrete()) {
			quad(buffers.getBuffer(RenderType.textSeeThrough(icon)), pose, left, right, 32, packedLight);
		}
		quad(buffers.getBuffer(RenderType.text(icon)), pose, left, right, 255, packedLight);
		poseStack.popPose();
	}

	private static void quad(VertexConsumer buffer, Matrix4f pose, float left, float right, int alpha, int light) {
		float top = -1f;
		float bottom = 9f;
		buffer.addVertex(pose, left, bottom, 0).setColor(255, 255, 255, alpha).setUv(0, 1).setLight(light);
		buffer.addVertex(pose, right, bottom, 0).setColor(255, 255, 255, alpha).setUv(1, 1).setLight(light);
		buffer.addVertex(pose, right, top, 0).setColor(255, 255, 255, alpha).setUv(1, 0).setLight(light);
		buffer.addVertex(pose, left, top, 0).setColor(255, 255, 255, alpha).setUv(0, 0).setLight(light);
	}
}
