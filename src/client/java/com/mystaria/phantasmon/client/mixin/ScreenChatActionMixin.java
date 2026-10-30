package com.mystaria.phantasmon.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Style;

import com.mystaria.phantasmon.client.command.ChatActions;

/** A click on one of Phantasmon's chat buttons runs its action ({@link ChatActions}) instead of a command. */
@Mixin(Screen.class)
public abstract class ScreenChatActionMixin {

	@Inject(method = "handleComponentClicked", at = @At("HEAD"), cancellable = true)
	private void phantasmon$chatAction(Style style, CallbackInfoReturnable<Boolean> cir) {
		if (ChatActions.handle(style)) {
			cir.setReturnValue(true);
		}
	}
}
