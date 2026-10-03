package com.mystaria.phantasmon.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.cobblemon.mod.common.client.gui.interact.wheel.InteractWheelGUI;
import com.cobblemon.mod.common.client.gui.interact.wheel.InteractWheelOption;
import com.cobblemon.mod.common.client.gui.interact.wheel.Orientation;
import com.google.common.collect.Multimap;

/**
 * Read access to the (private final) option map of Cobblemon's interaction wheel. The map is a plain mutable
 * {@code ArrayListMultimap} that {@code InteractWheelGUI.init()} turns into buttons when the screen opens, so
 * options put into it right after the wheel is created appear like Cobblemon's own.
 */
@Mixin(value = InteractWheelGUI.class, remap = false)
public interface InteractWheelGuiAccessor {

	@Accessor("options")
	Multimap<Orientation, InteractWheelOption> phantasmon$getOptions();
}
