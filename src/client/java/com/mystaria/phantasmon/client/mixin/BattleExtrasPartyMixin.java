package com.mystaria.phantasmon.client.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.mystaria.phantasmon.client.compat.BattleExtrasCompat;

/**
 * Cobblemon Battle Extras (optional): in a Phantasmon battle its "local party" is the team fighting, not the Cobblemon
 * party (Adrien 2026-11-08). Skipped when Battle Extras isn't installed.
 */
@Pseudo
@Mixin(targets = "name.modid.client.LocalBattlePartyTracker", remap = false)
public abstract class BattleExtrasPartyMixin {

	@Inject(method = { "getLocalPartySnapshot", "getLocalParty" }, at = @At("HEAD"), cancellable = true, require = 0)
	private static void phantasmon$battleTeam(CallbackInfoReturnable<List<Pokemon>> cir) {
		List<Pokemon> team = BattleExtrasCompat.ownParty();
		if (team != null) {
			cir.setReturnValue(new java.util.ArrayList<>(team));
		}
	}
}
