package com.mystaria.phantasmon.client.compat.emotecraft;

import java.util.UUID;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import io.github.kosmx.emotes.api.events.client.ClientEmoteAPI;
import io.github.kosmx.emotes.api.events.client.ClientEmoteEvents;
import io.github.kosmx.emotes.executor.emotePlayer.IEmotePlayerEntity;

/**
 * Emotecraft in the hubs (Adrien 2026-11-10): the emote a player plays is sent to the hub ({@code HubEmote}, its uuid)
 * and played on their avatar by the others who have the same emote (Emotecraft's built-in ones, or the same emote
 * file). Loaded only when Emotecraft is installed ({@link com.mystaria.phantasmon.client.hub.HubAvatars} checks).
 */
public final class HubEmotes {

	private static final Logger LOG = LoggerFactory.getLogger(HubEmotes.class);

	private HubEmotes() {
	}

	/** {@code send}: the local player's emote uuid, or null when it stops. */
	public static void register(Consumer<UUID> send) {
		ClientEmoteEvents.EMOTE_PLAY.register((animation, user) -> {
			if (local(user) && animation != null) {
				send.accept(animation.getUuid());
			}
		});
		ClientEmoteEvents.EMOTE_STOP.register((emote, user) -> {
			if (local(user)) {
				send.accept(null);
			}
		});
		ClientEmoteEvents.LOCAL_EMOTE_STOP.register(() -> send.accept(null));
	}

	private static boolean local(UUID user) {
		Minecraft mc = Minecraft.getInstance();
		return mc.player != null && mc.player.getUUID().equals(user);
	}

	/** Plays the emote {@code emote} on {@code avatar} (null: stops it); nothing if this client lacks that emote. */
	public static void play(Player avatar, UUID emote) {
		if (!(avatar instanceof IEmotePlayerEntity player)) {
			return;
		}
		try {
			if (emote == null) {
				player.stopEmote();
				return;
			}
			for (KeyframeAnimation animation : ClientEmoteAPI.clientEmoteList()) {
				if (emote.equals(animation.getUuid())) {
					player.emotecraft$playEmote(animation, 0, false);
					return;
				}
			}
			LOG.debug("Hub emote {} unknown here: not played", emote);
		} catch (RuntimeException | LinkageError ex) {
			LOG.debug("Hub emote {} not played", emote, ex);
		}
	}
}
