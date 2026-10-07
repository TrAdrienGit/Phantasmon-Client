package com.mystaria.phantasmon.client.wheel;

import java.util.UUID;

import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cobblemon.mod.common.client.gui.interact.wheel.InteractWheelOption;
import com.cobblemon.mod.common.client.gui.interact.wheel.Orientation;
import com.cobblemon.mod.common.net.messages.client.PlayerInteractOptionsPacket;
import com.cobblemon.mod.common.client.gui.interact.wheel.InteractWheelGUI;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import kotlin.Unit;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import com.mystaria.phantasmon.client.battle.LiveBattleController;
import com.mystaria.phantasmon.client.trade.LiveTradeController;

/**
 * The Phantasmon entries of Cobblemon's player-interaction wheel (R on a player): Ghost Trade and Ghost Battle,
 * each inviting the player the wheel is open on, and Watch Ghost battle (spectating the Ghost battle they play in,
 * Adrien 2026-10-07) — the same invites as the G and B keybinds, which
 * use the crosshair instead. Cobblemon's own trade/battle entries are untouched: they act on real Cobblemon
 * Pokémon, ours on Ghost Pokémon.
 *
 * <p>Placed on two orientations Cobblemon leaves free (it uses north and north-east). Icons are Cobblemon's own
 * white wheel icons, which the wheel tints with the colour supplied here.
 *
 * <p><b>On a Hub avatar</b> (Phantasmon Network, milestone 2): Cobblemon's wheel on a player is filled in by the
 * Minecraft server, which knows nothing of an avatar (a player of another server). {@link #openOnAvatar} opens the
 * same wheel locally with only the two Ghost entries — Cobblemon's own trade, battle and spectate act on that
 * server and cannot apply.
 */
public final class GhostWheelOptions {

	private static final Logger LOG = LoggerFactory.getLogger(GhostWheelOptions.class);

	private static LiveTradeController liveTrade;
	private static LiveBattleController liveBattle;

	private GhostWheelOptions() {
	}

	public static void bind(LiveTradeController trade, LiveBattleController battle) {
		liveTrade = trade;
		liveBattle = battle;
	}

	/** Called from the mixin on every wheel opened on a player; never lets a failure here break Cobblemon's own wheel. */
	public static void addTo(Multimap<Orientation, InteractWheelOption> options, PlayerInteractOptionsPacket packet) {
		if (liveTrade == null || liveBattle == null) {
			return;
		}
		try {
			UUID target = packet.getTargetId();
			options.put(Orientation.EAST, option("trade", "phantasmon.wheel.trade", new Vector3f(0.31f, 0.90f, 1.0f),
					() -> liveTrade.invitePlayer(target)));
			options.put(Orientation.NORTHWEST, option("battle", "phantasmon.wheel.battle", new Vector3f(1.0f, 0.31f, 0.47f),
					() -> liveBattle.invitePlayer(target)));
			options.put(Orientation.WEST, option("spectate_battle", "phantasmon.wheel.spectate", new Vector3f(0.55f, 0.95f, 0.55f),
					() -> liveBattle.spectatePlayer(target)));
		} catch (RuntimeException ex) {
			LOG.warn("Could not add the Ghost options to Cobblemon's interaction wheel", ex);
		}
	}

	/** The wheel for a player met in the Global Hub: Ghost Trade and Ghost Battle only, aimed at the real player. */
	public static void openOnAvatar(UUID playerUuid, String playerName) {
		if (liveTrade == null || liveBattle == null) {
			return;
		}
		Multimap<Orientation, InteractWheelOption> options = ArrayListMultimap.create();
		options.put(Orientation.EAST, option("trade", "phantasmon.wheel.trade", new Vector3f(0.31f, 0.90f, 1.0f),
				() -> liveTrade.invitePlayer(playerUuid)));
		options.put(Orientation.NORTHWEST, option("battle", "phantasmon.wheel.battle", new Vector3f(1.0f, 0.31f, 0.47f),
				() -> liveBattle.invitePlayer(playerUuid)));
		options.put(Orientation.WEST, option("spectate_battle", "phantasmon.wheel.spectate", new Vector3f(0.55f, 0.95f, 0.55f),
				() -> liveBattle.spectatePlayer(playerUuid)));
		Minecraft.getInstance().setScreen(new InteractWheelGUI(options, Component.literal(playerName)));
	}

	private static InteractWheelOption option(String icon, String tooltipKey, Vector3f tint, Runnable action) {
		ResourceLocation iconResource = ResourceLocation.fromNamespaceAndPath("cobblemon",
				"textures/gui/interact/interact_wheel_icon_" + icon + ".png");
		return new InteractWheelOption(iconResource, null, true, tooltipKey, () -> new Vector3f(tint), () -> {
			Minecraft.getInstance().setScreen(null);
			action.run();
			return Unit.INSTANCE;
		});
	}
}
