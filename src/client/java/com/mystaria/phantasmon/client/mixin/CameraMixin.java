package com.mystaria.phantasmon.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;

import com.mystaria.phantasmon.client.battle.BattleCinematic;

/**
 * Battle launch cinematic ({@link BattleCinematic}): while a shot plays, the camera is moved after vanilla — and
 * camera mods such as ShoulderSurfing (high priority, so this runs last) — have placed it. {@code detached} makes
 * the local player's own body render, as in third person.
 */
@Mixin(value = Camera.class, priority = 2000)
public abstract class CameraMixin {

	@Shadow
	private boolean detached;

	@Shadow
	protected abstract void setPosition(double x, double y, double z);

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Inject(method = "setup", at = @At("TAIL"))
	private void phantasmon$battleCinematic(BlockGetter level, Entity entity, boolean detachedArg, boolean mirrored,
			float partialTick, CallbackInfo ci) {
		Camera camera = (Camera) (Object) this;
		BattleCinematic.CameraPose pose = BattleCinematic.cameraPose(camera, partialTick);
		if (pose == null) {
			pose = com.mystaria.phantasmon.client.battle.BattleCameraDirector.cameraPose(camera, partialTick);
			// A Mega Evolution / Z-Move / Terastallization takes the camera, blending from and back to the shot below.
			BattleCinematic.CameraPose base = pose != null ? pose
					: new BattleCinematic.CameraPose(camera.getPosition(), camera.getYRot(), camera.getXRot(), detached);
			BattleCinematic.CameraPose spectacle = com.mystaria.phantasmon.client.battle.BattleSpectacle.cameraPose(camera, base, partialTick);
			if (spectacle != null) {
				pose = spectacle;
			}
		}
		if (pose != null) {
			setRotation(pose.yaw(), pose.pitch());
			setPosition(pose.position().x, pose.position().y, pose.position().z);
			detached = pose.detached() || detached;
		}
	}
}
