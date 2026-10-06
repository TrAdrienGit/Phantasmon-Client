package com.mystaria.phantasmon.client.mixin;

import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;

import com.mystaria.phantasmon.client.battle.BattleSpectacle;
import com.mystaria.phantasmon.client.battle.SpectacleOverlay;

/**
 * Battle set pieces ({@link BattleSpectacle}): their field-of-view effects (dolly zoom, punches), and their 2D layer
 * drawn last, over the HUD and every screen — Cobblemon's battle screen included.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererSpectacleMixin {

	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void phantasmon$spectacleFov(Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<Double> cir) {
		double scale = BattleSpectacle.fovScale();
		if (scale != 1.0) {
			cir.setReturnValue(cir.getReturnValue() * scale);
		}
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void phantasmon$spectacleOverlay(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
		if (!BattleSpectacle.playing()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		Window window = mc.getWindow();
		RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
		RenderSystem.backupProjectionMatrix();
		RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0f, (float) (window.getWidth() / window.getGuiScale()),
				(float) (window.getHeight() / window.getGuiScale()), 0f, 1000f, 21000f), VertexSorting.ORTHOGRAPHIC_Z);
		Matrix4fStack modelView = RenderSystem.getModelViewStack();
		modelView.pushMatrix();
		modelView.translation(0f, 0f, -11000f);
		RenderSystem.applyModelViewMatrix();
		GuiGraphics graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
		SpectacleOverlay.render(graphics);
		graphics.flush();
		modelView.popMatrix();
		RenderSystem.applyModelViewMatrix();
		RenderSystem.restoreProjectionMatrix();
	}
}
