package com.mystaria.phantasmon.client.wheel;

import java.util.UUID;

import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cobblemon.mod.common.client.gui.interact.wheel.InteractWheelOption;
import com.cobblemon.mod.common.client.gui.interact.wheel.Orientation;
import com.cobblemon.mod.common.net.messages.client.PlayerInteractOptionsPacket;
import com.google.common.collect.Multimap;

import kotlin.Unit;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import com.mystaria.phantasmon.client.battle.LiveBattleController;
import com.mystaria.phantasmon.client.trade.LiveTradeController;

/**
 * The two Phantasmon entries of Cobblemon's player-interaction wheel (R on a player): Ghost Trade and Ghost
 * Battle, each inviting the player the wheel is open on — the same invites as the G and B keybinds, which
 * use the crosshair instead. Cobblemon's own trade/battle entries are untouched: they act on real Cobblemon
 * Pokémon, ours on Ghost Pokémon.
 *
 * <p>Placed on two orientations Cobblemon leaves free (it uses north and north-east). Icons are Cobblemon's own
 * white wheel icons, which the wheel tints with the colour supplied here.
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
		} catch (RuntimeException ex) {
			LOG.warn("Could not add the Ghost options to Cobblemon's interaction wheel", ex);
		}
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
