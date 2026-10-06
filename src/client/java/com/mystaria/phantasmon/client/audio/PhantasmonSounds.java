package com.mystaria.phantasmon.client.audio;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.valueproviders.ConstantFloat;

/**
 * Interface sound effects (TODO-21, Adrien 2026-10-06), shipped in the mod itself (Adrien: not through a resource
 * pack, unlike {@link PhantasmonMusic}): {@code assets/phantasmon/sounds/sfx/<name>.ogg} of the mod's resources, no
 * JSON — the file itself is the sound (a resource pack could still replace it, as for any asset). Played on the Master
 * volume, like Minecraft's own button clicks. Client thread only.
 */
public final class PhantasmonSounds {

	public enum Sfx {
		/** The PC opens. */
		PC_LOGIN("pc_login"),
		/** The PC closes. */
		PC_LOGOUT("pc_logout"),
		/** A confirming button: ready (lobby, trade), import, export, edit, delete. */
		PRESSING_A("pressing_a");

		final ResourceLocation file;

		Sfx(String name) {
			this.file = ResourceLocation.fromNamespaceAndPath("phantasmon", "sfx/" + name);
		}
	}

	private PhantasmonSounds() {
	}

	public static void play(Sfx sfx) {
		Minecraft mc = Minecraft.getInstance();
		ResourceLocation resource = ResourceLocation.fromNamespaceAndPath("phantasmon", "sounds/" + sfx.file.getPath() + ".ogg");
		if (mc.getResourceManager().getResource(resource).isEmpty()) {
			return;
		}
		mc.getSoundManager().play(new SfxInstance(sfx.file));
	}

	/** One file played as is, not positional. */
	private static final class SfxInstance extends AbstractSoundInstance {
		private final ResourceLocation file;

		SfxInstance(ResourceLocation file) {
			super(SoundEvent.createVariableRangeEvent(file), SoundSource.MASTER, SoundInstance.createUnseededRandom());
			this.file = file;
			this.relative = true;
			this.attenuation = SoundInstance.Attenuation.NONE;
			this.volume = 1f;
		}

		/** Not declared in any sounds.json: the file itself is the sound. */
		@Override
		public WeighedSoundEvents resolve(SoundManager manager) {
			this.sound = new Sound(file, ConstantFloat.of(1f), ConstantFloat.of(1f), 1, Sound.Type.FILE, false, false, 16);
			return new WeighedSoundEvents(file, null);
		}
	}
}
