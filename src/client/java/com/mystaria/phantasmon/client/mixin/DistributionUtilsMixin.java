package com.mystaria.phantasmon.client.mixin;

import java.util.concurrent.CompletableFuture;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cobblemon.mod.common.util.DistributionUtilsKt;

import kotlin.jvm.functions.Function0;

import com.mystaria.phantasmon.client.battle.BattleThread;

/**
 * Pure-client Ghost battles (Phase 9). Cobblemon's battle engine hands every Showdown message to
 * {@code runOnServer}, which needs a {@code MinecraftServer}; on a client connected to a LAN or remote
 * server there is none, so each message was silently dropped and a battle hosted there never showed
 * anything (Adrien's second two-account test, 2026-10-03). Only for calls made from our private battle
 * thread, the block is queued on that same thread instead — exactly what {@code server.execute} does
 * for the integrated server. Every other caller keeps Cobblemon's behaviour.
 */
@Mixin(value = DistributionUtilsKt.class, remap = false)
public abstract class DistributionUtilsMixin {

	@Inject(method = "runOnServer", at = @At("HEAD"), cancellable = true)
	private static <T> void phantasmon$runOnBattleThread(Function0<? extends T> block, CallbackInfoReturnable<CompletableFuture<T>> cir) {
		if (!BattleThread.isOwnThread()) {
			return;
		}
		CompletableFuture<T> future = new CompletableFuture<>();
		BattleThread.get().executeOnOwnThread(() -> {
			try {
				future.complete(block.invoke());
			} catch (Throwable ex) {
				future.completeExceptionally(ex);
			}
		});
		cir.setReturnValue(future);
	}
}
