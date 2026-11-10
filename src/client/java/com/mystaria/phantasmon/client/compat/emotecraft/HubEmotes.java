package com.mystaria.phantasmon.client.compat.emotecraft;

import java.util.UUID;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import dev.kosmx.playerAnim.core.data.AnimationBinary;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import io.github.kosmx.emotes.api.events.client.ClientEmoteAPI;
import io.github.kosmx.emotes.api.events.client.ClientEmoteEvents;
import io.github.kosmx.emotes.executor.emotePlayer.IEmotePlayerEntity;

/**
 * Emotecraft in the hubs (Adrien 2026-11-10): the emote a player plays is sent to the hub — first its whole animation
 * (binary frame {@code [0x02][version int32][emote uuid][animation]}, playerAnimator's own {@link AnimationBinary}
 * encoding, as Emotecraft sends emotes through a Minecraft server), then {@code HubEmote} (its uuid) — and played on
 * their avatar by the others, even those who don't have that emote. Loaded only when Emotecraft is installed.
 */
public final class HubEmotes {

	private static final Logger LOG = LoggerFactory.getLogger(HubEmotes.class);

	private HubEmotes() {
	}

	static final byte KIND_EMOTE = 0x02;
	/** The backend's limit (HubEmoteFrame.MAX_BYTES) minus our header: bigger emotes go by uuid only. */
	private static final int MAX_ANIMATION_BYTES = 64 * 1024 - 4 - 16;
	/** Animations received from the others, by emote uuid (the newest 64). */
	private static final java.util.Map<UUID, KeyframeAnimation> RECEIVED = java.util.Collections.synchronizedMap(
			new java.util.LinkedHashMap<>(16, 0.75f, true) {
				@Override
				protected boolean removeEldestEntry(java.util.Map.Entry<UUID, KeyframeAnimation> eldest) {
					return size() > 64;
				}
			});

	/**
	 * {@code send}: the local player's emote uuid, or null when it stops; {@code sendAnimation}: the binary frame of
	 * its whole animation, sent just before.
	 */
	public static void register(Consumer<UUID> send, Consumer<byte[]> sendAnimation) {
		ClientEmoteEvents.EMOTE_PLAY.register((animation, user) -> {
			if (local(user) && animation != null) {
				byte[] frame = encode(animation);
				if (frame != null) {
					sendAnimation.accept(frame);
				}
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

	private static byte[] encode(KeyframeAnimation animation) {
		try {
			int version = AnimationBinary.getCurrentVersion();
			int size = AnimationBinary.calculateSize(animation, version);
			if (size <= 0 || size > MAX_ANIMATION_BYTES) {
				LOG.debug("Hub emote {} too big to send ({} bytes): sent by uuid only", animation.getUuid(), size);
				return null;
			}
			java.nio.ByteBuffer frame = java.nio.ByteBuffer.allocate(1 + 4 + 16 + size);
			frame.put(KIND_EMOTE).putInt(version)
					.putLong(animation.getUuid().getMostSignificantBits()).putLong(animation.getUuid().getLeastSignificantBits());
			AnimationBinary.write(animation, frame, version);
			return java.util.Arrays.copyOf(frame.array(), frame.position());
		} catch (RuntimeException | LinkageError ex) {
			LOG.debug("Hub emote {} not encoded", animation.getUuid(), ex);
			return null;
		}
	}

	/**
	 * A binary frame from the backend {@code [0x02][sender uuid][version int32][emote uuid][animation]}: kept, played as
	 * soon as its {@code HubPlayerEmote} arrives (right after). WebSocket thread.
	 */
	public static void onFrame(java.nio.ByteBuffer data) {
		try {
			java.nio.ByteBuffer in = data.duplicate();
			if (in.remaining() < 1 + 16 + 4 + 16 || in.get() != KIND_EMOTE) {
				return;
			}
			in.getLong();
			in.getLong(); // the sender: HubPlayerEmote says whose avatar plays it
			int version = in.getInt();
			UUID emote = new UUID(in.getLong(), in.getLong());
			KeyframeAnimation animation = AnimationBinary.read(in.slice(), version);
			if (animation != null) {
				RECEIVED.put(emote, animation);
			}
		} catch (java.io.IOException | RuntimeException | LinkageError ex) {
			LOG.debug("Hub emote animation not read", ex);
		}
	}

	/** Plays the emote {@code emote} on {@code avatar} (null: stops it): the animation received, else this client's own. */
	public static void play(Player avatar, UUID emote) {
		if (!(avatar instanceof IEmotePlayerEntity player)) {
			return;
		}
		try {
			if (emote == null) {
				player.stopEmote();
				return;
			}
			KeyframeAnimation received = RECEIVED.get(emote);
			if (received != null) {
				player.emotecraft$playEmote(received, 0, false);
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
