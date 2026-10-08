package com.mystaria.phantasmon.client.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;

import com.mystaria.phantasmon.client.hub.HubBuilds;

/**
 * Hub builds (D-34): their client-only blocks can't be broken (Adrien's rule) — no mining, no crack, nothing sent —
 * and a right click on one never reaches the server (which would place a block in the air there); doors, trapdoors
 * and fence gates open and close locally ({@link HubBuilds#use}).
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeHubBuildMixin {

	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "startDestroyBlock", at = @At("HEAD"), cancellable = true)
	private void phantasmon$unbreakableStart(BlockPos pos, Direction face, CallbackInfoReturnable<Boolean> cir) {
		if (HubBuilds.isFake(minecraft.level, pos)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "continueDestroyBlock", at = @At("HEAD"), cancellable = true)
	private void phantasmon$unbreakableContinue(BlockPos pos, Direction face, CallbackInfoReturnable<Boolean> cir) {
		if (HubBuilds.isFake(minecraft.level, pos)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
	private void phantasmon$unbreakable(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		if (HubBuilds.isFake(minecraft.level, pos)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
	private void phantasmon$useHubBuild(LocalPlayer player, InteractionHand hand, BlockHitResult hit,
			CallbackInfoReturnable<InteractionResult> cir) {
		if (HubBuilds.isFake(minecraft.level, hit.getBlockPos())) {
			cir.setReturnValue(hand == InteractionHand.MAIN_HAND ? HubBuilds.use(minecraft.level, hit) : InteractionResult.PASS);
		}
	}
}
