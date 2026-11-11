package com.mystaria.phantasmon.client.battle;

import net.minecraft.network.chat.Component;

import com.mystaria.phantasmon.client.hub.HubAvatars;
import com.mystaria.showtime.api.BattleButton;
import com.mystaria.showtime.api.ShowtimeHooks;

/**
 * What Phantasmon plugs into Cobblemon Battle Showtime (D-37), which films its Ghost battles: Hub avatars as stand-ins for
 * players of another server, Tera glows kept by the battles seen from afar, the trainers' animation profiles
 * ({@link TrainerProfiles}), and the turn timer button under
 * Showtime's music and camera buttons during a Ghost battle.
 */
public final class ShowtimeBridge {

	private ShowtimeBridge() {
	}

	public static void register(LiveBattleController controller) {
		ShowtimeHooks.setPlayerResolver(HubAvatars::playerOrAvatar);
		ShowtimeHooks.setGlowKeeper(BattleFieldScenes::owns);
		// Showtime's menu (key N) and settings open the Phantasmon menu on its Music page.
		ShowtimeHooks.setMusicScreen(com.mystaria.phantasmon.client.gui.menu.PhantasmonMenuScreen::music);
		// The lobby plays its own music folder (Phantasmon Resource Pack): Showtime alone has no lobby.
		ShowtimeHooks.enableLobbyMusic();
		// Ghost battles are staged by BattleVisuals: Showtime's own staging of Cobblemon battles leaves them alone.
		ShowtimeHooks.setForeignPacketCheck(CobblemonPackets::dispatching);
		// The trainers' animation profiles, shared through the backend (Showtime phase E3).
		TrainerProfiles.register();
		// The ambiance picked in the lobby, the same for both players (Showtime A3).
		ShowtimeHooks.setAmbianceOverride(controller::battleAmbiance);
		// Reserved intros and ambiances (TODO-37): only those the player won.
		com.mystaria.phantasmon.client.pokemon.RewardContent.register();
		// Once on, the timer button becomes the countdown (Adrien 2026-10-05).
		ShowtimeHooks.addButton(new BattleButton(BattleButton.CAMERA_ORDER + 10, BattleVisuals::filmed,
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
