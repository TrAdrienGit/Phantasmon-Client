package com.mystaria.phantasmon.client.gui;

import com.cobblemon.mod.common.api.scheduling.ClientTaskTracker;
import com.cobblemon.mod.common.api.scheduling.SchedulingTracker;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState;

import net.minecraft.world.entity.Entity;

/**
 * Cobblemon's {@code FloatingState} without the animation effects (2026-11-28): the time adds up frame after frame so
 * a menu model plays its animations, but the sounds and particles keyed in them (a cry's sound) never run.
 */
final class SilentGuiState extends PosableState {

	@Override
	public Entity getEntity() {
		return null;
	}

	@Override
	public SchedulingTracker getSchedulingTracker() {
		return ClientTaskTracker.INSTANCE;
	}

	@Override
	public void updatePartialTicks(float partialTicks) {
		// FloatingState also calls runEffects here: that is what plays the sounds.
		setCurrentPartialTicks(getCurrentPartialTicks() + partialTicks);
	}
}
