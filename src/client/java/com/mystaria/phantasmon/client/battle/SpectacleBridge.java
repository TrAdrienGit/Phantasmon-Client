package com.mystaria.phantasmon.client.battle;

import net.minecraft.network.chat.Component;

import com.mystaria.phantasmon.client.hub.HubAvatars;
import com.mystaria.spectacle.api.BattleButton;
import com.mystaria.spectacle.api.SpectacleHooks;

/**
 * What Phantasmon plugs into Cobblemon Spectacle (D-37), which films its Ghost battles: Hub avatars as stand-ins for
 * players of another server, Tera glows kept by the battles seen from afar, and the turn timer button under
 * Spectacle's music and camera buttons during a Ghost battle.
 */
public final class SpectacleBridge {

	private SpectacleBridge() {
	}

	public static void register(LiveBattleController controller) {
		SpectacleHooks.setPlayerResolver(HubAvatars::playerOrAvatar);
		SpectacleHooks.setGlowKeeper(BattleFieldScenes::owns);
		// Spectacle's music key (N) and settings open Phantasmon's music screen, in Phantasmon's look.
		SpectacleHooks.setMusicScreen(com.mystaria.phantasmon.client.gui.PhantasmonMusicScreen::new);
		// Ghost battles are staged by BattleVisuals: Spectacle's own staging of Cobblemon battles leaves them alone.
		SpectacleHooks.setForeignPacketCheck(CobblemonPackets::dispatching);
		// Once on, the timer button becomes the countdown (Adrien 2026-10-05).
		SpectacleHooks.addButton(new BattleButton(BattleButton.CAMERA_ORDER + 10, BattleVisuals::filmed,
				() -> {
					long left = controller.timerSecondsLeft();
					return !controller.timerOn() ? Component.translatable("phantasmon.battle.buttons.timer").getString()
							: left < 0 ? Component.translatable("phantasmon.battle.buttons.timer_waiting").getString()
							: "⏱ " + left + " s";
				},
				() -> {
					long left = controller.timerSecondsLeft();
					return !controller.timerOn() ? BattleButton.CYAN : left >= 0 && left <= 10 ? BattleButton.RED : BattleButton.GREEN;
				},
				() -> !controller.timerOn(), controller::enableTimer));
	}
}
