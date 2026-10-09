package com.mystaria.phantasmon.client.voice;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import com.mystaria.phantasmon.client.ghost.GhostSession;
import com.mystaria.phantasmon.client.hub.HubAvatars;

/**
 * Voice in the hubs (D-36), through the players' own <b>Simple Voice Chat</b>, an optional mod: Phantasmon never
 * requires it nor loads any of its classes here. When it is installed, its plugin entrypoint
 * ({@code compat.voicechat.PhantasmonVoicechatPlugin}) plugs an {@link Engine} in; when its client is connected to a
 * voice server (the player's own Minecraft server or world runs it — the only way its microphone and speakers work),
 * the player talks and listens in the hub:
 * <ul>
 * <li>what Simple Voice Chat records while the player talks (push-to-talk or voice activation, its own settings) is
 * encoded and sent to the backend as binary frames, which relays them to the hub members with voice;</li>
 * <li>a frame received is played from the speaker's avatar, in 3D, like Simple Voice Chat's proximity voice.</li>
 * </ul>
 * Without the mod, or disconnected, the player is in the hub without voice (D-36: never blocking).
 */
public final class HubVoice {

	private static final Logger LOG = LoggerFactory.getLogger(HubVoice.class);
	static final byte KIND_VOICE = 0x01;
	static final int FLAG_WHISPERING = 0x01;
	private static final int HEADER = 1 + 1 + 4;
	private static final int SPEAKER_HEADER = HEADER + 16;

	/** What Simple Voice Chat provides; implemented in its plugin only. */
	public interface Engine {
		/** Its client is connected to a voice server: microphone and speakers work. */
		boolean connected();

		/** Plays an Opus frame of {@code speaker} from {@code source} (their avatar). Client thread. */
		void play(UUID speaker, Entity source, byte[] opus, boolean whispering);

		/** {@code speaker} left: their decoder can go. */
		void forget(UUID speaker);

		/** Out of the hub: every decoder goes. */
		void forgetAll();
	}

	private static volatile Engine engine;
	private static volatile GhostSession session;
	/** In a hub (from {@code HubJoined} to {@code HubLeft}). */
	private static volatile boolean inHub;
	/** What the backend was last told ({@code HubVoiceState}), null before. */
	private static Boolean announced;
	private static int sequence;
	private static int ticks;

	private HubVoice() {
	}

	/** Simple Voice Chat's plugin, once it is initialised. */
	public static void setEngine(Engine voiceEngine) {
		engine = voiceEngine;
		LOG.info("Simple Voice Chat found: hub voice available when its client is connected");
	}

	public static void bind(GhostSession ghostSession) {
		session = ghostSession;
	}

	/** Whether Simple Voice Chat is installed. */
	public static boolean installed() {
		return engine != null;
	}

	private static boolean usable() {
		Engine current = engine;
		return current != null && current.connected();
	}

	// ---------------------------------------------------------------- hub lifecycle (client thread)

	/** {@code HubJoined}: tells the backend whether we talk and listen, and the player whether voice works. */
	public static void onJoined() {
		inHub = true;
		announced = null;
		announce();
		if (engine == null) {
			return;
		}
		chat(usable() ? Component.translatable("phantasmon.hub.voice.on").withStyle(ChatFormatting.LIGHT_PURPLE)
				: Component.translatable("phantasmon.hub.voice.unavailable").withStyle(ChatFormatting.GRAY));
	}

	/** Out of the hub (left, connection lost, world left). */
	public static void onLeft() {
		inHub = false;
		announced = null;
		Engine current = engine;
		if (current != null) {
			current.forgetAll();
		}
	}

	/** A member left: their decoder goes. */
	public static void onMemberLeft(UUID playerUuid) {
		Engine current = engine;
		if (current != null && playerUuid != null) {
			current.forget(playerUuid);
		}
	}

	/** Every client tick: Simple Voice Chat may connect or drop meanwhile — the backend is told (once a second). */
	public static void tick() {
		if (inHub && ++ticks % 20 == 0) {
			announce();
		}
	}

	private static void announce() {
		GhostSession current = session;
		boolean enabled = usable();
		if (current != null && inHub && (announced == null || announced != enabled)
				&& current.send("HubVoiceState", Map.of("enabled", enabled))) {
			announced = enabled;
		}
	}

	// ---------------------------------------------------------------- talking (Simple Voice Chat's microphone thread)

	/** Whether what the microphone records now should be encoded for the hub. */
	public static boolean capturing() {
		return inHub && Boolean.TRUE.equals(announced) && usable();
	}

	/** One encoded frame of our voice, for the hub members. */
	public static void send(byte[] opus, boolean whispering) {
		GhostSession current = session;
		if (current == null || opus == null || opus.length == 0) {
			return;
		}
		ByteBuffer frame = ByteBuffer.allocate(HEADER + opus.length);
		frame.put(KIND_VOICE).put((byte) (whispering ? FLAG_WHISPERING : 0)).putInt(sequence++).put(opus);
		current.sendBinary(frame.array());
	}

	// ---------------------------------------------------------------- listening (WebSocket thread)

	/** A binary message from the backend: a member's voice frame, played from their avatar. */
	public static void onFrame(ByteBuffer data) {
		Engine current = engine;
		if (current == null || data.remaining() <= SPEAKER_HEADER || data.get() != KIND_VOICE) {
			return;
		}
		boolean whispering = (data.get() & FLAG_WHISPERING) != 0;
		data.getInt(); // sequence: Opus copes with a lost frame, kept for later use
		UUID speaker = new UUID(data.getLong(), data.getLong());
		byte[] opus = new byte[data.remaining()];
		data.get(opus);
		Minecraft.getInstance().execute(() -> {
			if (!inHub || !current.connected()) {
				return;
			}
			// No avatar (the real player stands here, hidden avatar): Simple Voice Chat of this server already carries them.
			Entity avatar = HubAvatars.shownAvatar(speaker);
			if (avatar != null) {
				try {
					current.play(speaker, avatar, opus, whispering);
				} catch (RuntimeException ex) {
					LOG.debug("Hub voice frame of {} not played", speaker, ex);
				}
			}
		});
	}

	private static void chat(Component message) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.displayClientMessage(message, false);
		}
	}
}
