package com.mystaria.phantasmon.client.mixin;

import java.util.concurrent.CompletableFuture;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.cobblemon.mod.common.battles.interpreter.instructions.MoveInstruction;

import kotlin.Unit;

/**
 * Unlike the other instructions, {@code MoveInstruction} (a final Kotlin class) stores
 * {@code future = actionEffect.run(context)} straight into its field, never through {@code setFuture} — so
 * {@code ActionEffectInstructionsMixin} never saw moves, and move animations played without user or target
 * (Adrien's test, 2026-10-03: boosts and statuses animated, moves didn't). This turns that one field write,
 * inside the dispatch lambda that runs the move's action effect ({@code invoke$lambda$1} in Cobblemon 1.8.1),
 * into a {@code setFuture} call, which stores the same value and is seen by the binding mixin.
 */
@Mixin(value = MoveInstruction.class, remap = false)
public abstract class MoveInstructionMixin {

	@Redirect(method = "invoke$lambda$1",
			at = @At(value = "FIELD", target = "Lcom/cobblemon/mod/common/battles/interpreter/instructions/MoveInstruction;future:Ljava/util/concurrent/CompletableFuture;",
					opcode = Opcodes.PUTFIELD))
	private static void phantasmon$storeThroughSetter(MoveInstruction instruction, CompletableFuture<Unit> future) {
		instruction.setFuture(future);
	}
}
