package com.mystaria.phantasmon.client.compat.voicechat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatClientApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VolumeCategory;
import de.maxhenkel.voicechat.api.audiochannel.ClientEntityAudioChannel;
import de.maxhenkel.voicechat.api.events.ClientSoundEvent;
import de.maxhenkel.voicechat.api.events.ClientVoicechatConnectionEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.entity.Entity;

import com.mystaria.phantasmon.client.voice.HubVoice;

/**
 * Simple Voice Chat's plugin entrypoint ({@code "voicechat"} in {@code fabric.mod.json}, D-36): only ever loaded by
 * Simple Voice Chat itself, so its API is touched nowhere else and Phantasmon runs the same without the mod. Written
 * against the oldest 2.5 API (the modpack's 1.21.1 line).
 *
 * <ul>
 * <li>{@link ClientSoundEvent}: the raw audio Simple Voice Chat is about to send to its own server while the player
 * talks — also encoded (Opus, Simple Voice Chat's own codec) for the hub when the player is in one;</li>
 * <li>each hub speaker gets a decoder and an entity audio channel on their avatar (positional, at the voice server's
 * distance, in the "Phantasmon hub" volume category the player can set in Simple Voice Chat's menu).</li>
 * </ul>
 */
public final class PhantasmonVoicechatPlugin implements VoicechatPlugin, HubVoice.Engine {

	private static final Logger LOG = LoggerFactory.getLogger(PhantasmonVoicechatPlugin.class);
	/** Volume category id: lower case, at most 16 characters. */
	private static final String CATEGORY = "phantasmon_hub";

	private VoicechatApi api;
	private volatile VoicechatClientApi client;
	private volatile boolean connected;
	private boolean categoryRegistered;
	/** Microphone thread only. */
	private OpusEncoder encoder;
	/** Client thread only: speaker → decoder, channel. */
	private final Map<UUID, Speaker> speakers = new HashMap<>();

	private record Speaker(OpusDecoder decoder, ClientEntityAudioChannel channel) {
	}

	@Override
	public String getPluginId() {
		return "phantasmon";
	}

	@Override
	public void initialize(VoicechatApi voicechatApi) {
		this.api = voicechatApi;
		HubVoice.setEngine(this);
	}

	@Override
	public void registerEvents(EventRegistration registration) {
		registration.registerEvent(ClientVoicechatConnectionEvent.class, this::onConnection);
		registration.registerEvent(ClientSoundEvent.class, this::onMicrophone);
	}

	private void onConnection(ClientVoicechatConnectionEvent event) {
		client = event.getVoicechat();
		connected = event.isConnected();
		if (connected && !categoryRegistered && api != null) {
			try {
				client.registerClientVolumeCategory(api.volumeCategoryBuilder()
						.setId(CATEGORY)
						.setName(I18n.get("phantasmon.hub.voice.category"))
						.setDescription(I18n.get("phantasmon.hub.voice.category.description"))
						.build());
				categoryRegistered = true;
			} catch (RuntimeException ex) {
				LOG.warn("Could not register the Phantasmon hub volume category", ex);
			}
		}
	}

	private void onMicrophone(ClientSoundEvent event) {
		client = event.getVoicechat();
		if (!HubVoice.capturing() || client.isMuted() || api == null) {
			return;
		}
		short[] audio = event.getRawAudio();
		if (audio == null || audio.length == 0) {
			return;
		}
		if (encoder == null || encoder.isClosed()) {
			encoder = api.createEncoder();
		}
		HubVoice.send(encoder.encode(audio), event.isWhispering());
	}

	// ---------------------------------------------------------------- HubVoice.Engine

	@Override
	public boolean connected() {
		VoicechatClientApi current = client;
		return connected && current != null && !current.isDisconnected();
	}

	@Override
	public void play(UUID speakerUuid, Entity source, byte[] opus, boolean whispering) {
		VoicechatClientApi current = client;
		if (current == null || current.isDisabled() || api == null) {
			return;
		}
		Speaker speaker = speakers.get(speakerUuid);
		if (speaker == null || !speaker.channel().getId().equals(source.getUUID())) {
			if (speaker != null) {
				speaker.decoder().close();
			}
			// The channel is the avatar's entity: Simple Voice Chat finds it by its uuid and plays from its position.
			ClientEntityAudioChannel channel = current.createEntityAudioChannel(source.getUUID());
			if (categoryRegistered) {
				channel.setCategory(CATEGORY);
			}
			speaker = new Speaker(api.createDecoder(), channel);
			speakers.put(speakerUuid, speaker);
		}
		// The voice server's distance (48 by default); a server sending none gets Simple Voice Chat's default.
		double distance = api.getVoiceChatDistance();
		speaker.channel().setDistance((float) (distance > 0 ? distance : 48));
		speaker.channel().setWhispering(whispering);
		speaker.channel().play(speaker.decoder().decode(opus));
	}

	@Override
	public void forget(UUID speakerUuid) {
		Speaker speaker = speakers.remove(speakerUuid);
		if (speaker != null) {
			speaker.decoder().close();
		}
	}

	@Override
	public void forgetAll() {
		speakers.values().forEach(speaker -> speaker.decoder().close());
		speakers.clear();
	}
}
