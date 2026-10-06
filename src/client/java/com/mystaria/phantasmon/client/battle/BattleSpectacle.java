package com.mystaria.phantasmon.client.battle;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.joml.Vector3f;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.net.messages.client.animation.PlayPosableAnimationPacket;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The battle gimmicks as set pieces (Adrien 2026-10-06: "un effet whoua"), on both clients' scene. While one plays,
 * the camera is taken ({@code CameraMixin}, {@code GameRendererFovMixin}), Cobblemon's battle screen and the HUD are
 * hidden, the 2D layer is drawn by {@link SpectacleOverlay}, and the host's engine waits for it
 * ({@link #pauseSeconds}, {@link GhostBattles}).
 *
 * <ul>
 *   <li><b>Mega Evolution</b> (after X/Y's): the scene darkens, motes of light are drawn into the Pokémon, a white
 *   cocoon wrapped in swirling pink ribbons, rainbow flares, the shell turns lilac and cracks with golden light,
 *   shatters — the model switches in the flash — and the Mega symbol appears over a rainbow burst.
 *   <b>Primal Reversion</b>: the same in Groudon's red / Kyogre's blue, with Ω / α.</li>
 *   <li><b>Z-Move</b>: golden aura, rising rings, a dolly zoom onto the Pokémon under a spinning Z-Crystal emblem,
 *   a fast orbit, then the Z-Power released in a shockwave.</li>
 *   <li><b>Terastallization</b>: crystal shards in the Tera type's colour spiral in and encase the Pokémon in a
 *   faceted crystal, which shatters under the Tera jewel; from then on the Pokémon glows in its Tera colour with
 *   floating motes and shards, until the end of the battle (kept through switches).</li>
 * </ul>
 * Client thread only.
 */
public final class BattleSpectacle {

	public enum Kind {
		MEGA(5.2f), PRIMAL(5.2f), Z_MOVE(3.8f), TERA(4.0f);

		final float seconds;

		Kind(float seconds) {
			this.seconds = seconds;
		}
	}

	/** When each set piece's climax happens (model switch, release, shatter), in seconds. */
	static final float MEGA_SHATTER = 3.3f;
	static final float Z_RELEASE = 2.6f;
	static final float TERA_SHATTER = 2.3f;

	private static final Random RANDOM = new Random();

	private static Kind kind;
	private static PokemonEntity entity;
	private static FormeChangeVisual change;
	private static long start = -1;
	/** Tera colour (RGB) of the set piece playing; for Stellar, cycles. */
	private static String teraType;
	private static boolean climaxDone;
	private static final Set<String> cues = new HashSet<>();

	/** Battle Pokémon uuid → Tera type, for the whole battle (its entity is rebuilt on each switch-in). */
	private static final Map<String, String> teraByPokemon = new HashMap<>();
	/** Entity id → Tera type, for the glow ({@code EntityGlowMixin}) and the floating particles. */
	private static final Map<Integer, String> glowing = new HashMap<>();
	/** Entity id → when a preview's glow goes away (admin preview only). */
	private static final Map<Integer, Long> glowUntil = new HashMap<>();

	private BattleSpectacle() {
	}

	// =====================================================================
	// Lifecycle
	// =====================================================================

	/** How long the host's engine waits for the set piece of this change ({@link FormeChangeVisual#aspect()}). */
	public static float pauseSeconds(String aspect) {
		return kindOf(aspect).seconds;
	}

	private static Kind kindOf(String aspect) {
		if (FormeChangeVisual.Z_POWER.equals(aspect)) {
			return Kind.Z_MOVE;
		}
		if (aspect != null && aspect.startsWith(FormeChangeVisual.TERA_PREFIX)) {
			return Kind.TERA;
		}
		return "primal".equals(aspect) ? Kind.PRIMAL : Kind.MEGA;
	}

	/** A Mega Evolution / Primal Reversion / Z-Move / Terastallization announced by the battle (host or relayed). */
	public static void play(FormeChangeVisual visual) {
		Kind which = kindOf(visual.aspect());
		if (which == Kind.TERA) {
			teraByPokemon.put(visual.uuid(), visual.teraType());
		}
		PokemonEntity target = BattleVisuals.entityAt(visual.pnx());
		if (target == null) {
			return;
		}
		begin(which, target, visual, which == Kind.TERA ? visual.teraType() : null);
	}

	/** Admin preview ({@code /phantasmon admin debug spectacle}): on any Pokémon in the world, nothing changes. */
	public static void preview(Kind which, PokemonEntity target, String type) {
		begin(which, target, null, type == null ? "fire" : type);
	}

	private static void begin(Kind which, PokemonEntity target, FormeChangeVisual visual, String type) {
		if (playing()) {
			finish();
		}
		kind = which;
		entity = target;
		change = visual;
		teraType = type;
		start = System.currentTimeMillis();
		climaxDone = false;
		cues.clear();
	}

	public static boolean playing() {
		return start >= 0 && entity != null;
	}

	/** Seconds since the set piece began. */
	static float time() {
		return start < 0 ? 0f : (System.currentTimeMillis() - start) / 1000f;
	}

	static Kind kind() {
		return kind;
	}

	static PokemonEntity entity() {
		return entity;
	}

	static String species() {
		return change == null ? (entity == null ? "" : entity.getPokemon().getSpecies().getResourceIdentifier().getPath()) : change.species();
	}

	/** RGB of the Tera type being shown ({@link #teraColor}). */
	static int color() {
		return teraColor(teraType);
	}

	/** A Pokémon was sent out: if it terastallized earlier in this battle, it glows again. */
	public static void onSentOut(PokemonEntity sent, java.util.UUID battlePokemonUuid) {
		String type = battlePokemonUuid == null ? null : teraByPokemon.get(battlePokemonUuid.toString());
		if (type != null) {
			glowing.put(sent.getId(), type);
		}
	}

	/** Battle over / scene cleared. */
	public static void clear() {
		if (playing()) {
			finish();
		}
		teraByPokemon.clear();
		glowing.clear();
		glowUntil.clear();
	}

	private static void finish() {
		if (!climaxDone) {
			climax();
		}
		start = -1;
		entity = null;
		change = null;
		kind = null;
	}

	// =====================================================================
	// Glow (EntityGlowMixin)
	// =====================================================================

	/** The Tera glow's colour for this entity this frame, or -1: a slow shimmer toward white. */
	public static int glowColor(Entity candidate) {
		if (glowing.isEmpty()) {
			return -1;
		}
		String type = glowing.get(candidate.getId());
		if (type == null) {
			return -1;
		}
		float shimmer = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 260.0);
		return mix(teraColor(type), 0xFFFFFF, 0.15f + 0.3f * shimmer);
	}

	// =====================================================================
	// Tick: particles and sounds
	// =====================================================================

	public static void tick() {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		tickGlowing(level);
		if (!playing()) {
			return;
		}
		if (entity.isRemoved()) {
			finish();
			return;
		}
		float t = time();
		if (t >= kind.seconds) {
			finish();
			return;
		}
		switch (kind) {
			case MEGA, PRIMAL -> tickMega(level, t);
			case Z_MOVE -> tickZMove(level, t);
			case TERA -> tickTera(level, t);
		}
	}

	private static void tickGlowing(ClientLevel level) {
		long now = System.currentTimeMillis();
		Iterator<Map.Entry<Integer, String>> it = glowing.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Integer, String> entry = it.next();
			Entity glowingEntity = level.getEntity(entry.getKey());
			Long until = glowUntil.get(entry.getKey());
			if (glowingEntity == null || glowingEntity.isRemoved() || (until != null && now > until)) {
				glowUntil.remove(entry.getKey());
				it.remove();
				continue;
			}
			int rgb = teraColor(entry.getValue());
			double radius = Math.max(0.5, glowingEntity.getBbWidth() * 0.7);
			double height = glowingEntity.getBbHeight();
			// Motes drifting up round the Pokémon, now and then a sparkle or a crystal chip.
			for (int i = 0; i < 2; i++) {
				double angle = RANDOM.nextDouble() * Math.PI * 2;
				double r = radius * (0.6 + 0.6 * RANDOM.nextDouble());
				level.addParticle(dust(rgb, 0.7f + RANDOM.nextFloat() * 0.6f),
						glowingEntity.getX() + Math.cos(angle) * r, glowingEntity.getY() + RANDOM.nextDouble() * height,
						glowingEntity.getZ() + Math.sin(angle) * r, 0, 0.03, 0);
			}
			if (RANDOM.nextInt(5) == 0) {
				double angle = RANDOM.nextDouble() * Math.PI * 2;
				level.addParticle(ParticleTypes.END_ROD, glowingEntity.getX() + Math.cos(angle) * radius,
						glowingEntity.getY() + height * (0.3 + 0.7 * RANDOM.nextDouble()), glowingEntity.getZ() + Math.sin(angle) * radius,
						0, 0.015, 0);
			}
			if (RANDOM.nextInt(8) == 0) {
				level.addParticle(shard(entry.getValue()), glowingEntity.getX(), glowingEntity.getY() + height * 0.8, glowingEntity.getZ(),
						0, 0.1, 0);
			}
		}
	}

	private static void tickMega(ClientLevel level, float t) {
		boolean primal = kind == Kind.PRIMAL;
		boolean kyogre = "kyogre".equals(species());
		int[] palette = primal ? (kyogre ? new int[] { 0x2A6CFF, 0x7FE0FF, 0xFFFFFF } : new int[] { 0xFF3A1A, 0xFFB030, 0xFFFFFF })
				: new int[] { 0xFF5FD2, 0xB45CFF, 0xFFFFFF, 0xFFE070 };
		Vec3 c = center();
		double r = radius();
		cue("start", t >= 0f, primal ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_ACTIVATE, primal ? 0.6f : 1.3f, 1f);
		cue("hum1", t >= 0.5f, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8f, 1f);
		cue("hum2", t >= 0.8f, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 1f);
		cue("hum3", t >= 1.1f, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.3f, 1f);
		cue("cocoon", t >= 1.3f, SoundEvents.BEACON_AMBIENT, primal ? 0.8f : 1.5f, 1f);
		cue("swell", t >= 2.5f, SoundEvents.BEACON_POWER_SELECT, primal ? 0.5f : 0.8f, 1f);
		cue("crack", t >= 2.9f, SoundEvents.AMETHYST_BLOCK_BREAK, 0.6f, 1f);
		cue("cry", t >= MEGA_SHATTER + 0.5f, null, 1f, 1f);

		if (t >= 0.4f && t < 1.6f) {
			// Motes of light drawn into the Pokémon.
			for (int i = 0; i < 6; i++) {
				Vec3 from = c.add(randomUnit().scale(r * 4.5));
				Vec3 velocity = c.subtract(from).scale(1 / 22.0);
				level.addParticle(ParticleTypes.END_ROD, from.x, from.y, from.z, velocity.x, velocity.y, velocity.z);
			}
		}
		if (t >= 1.2f && t < MEGA_SHATTER) {
			// The ribbons' double helix round the cocoon.
			int tick = Math.round(t * 20);
			for (int strand = 0; strand < 3; strand++) {
				for (int k = 0; k < 2; k++) {
					double progress = ((tick * 2 + k + strand * 7) % 30) / 30.0;
					double angle = progress * Math.PI * 4 + strand * Math.PI * 2 / 3 + t * 5;
					double ring = r * 1.35;
					level.addParticle(dust(palette[(strand + k) % palette.length], 1.6f),
							c.x + Math.cos(angle) * ring, c.y - r * 1.1 + progress * r * 2.4, c.z + Math.sin(angle) * ring, 0, 0, 0);
				}
			}
			if (t >= 2.4f) {
				// Flares licking up from below.
				for (int i = 0; i < 4; i++) {
					double angle = RANDOM.nextDouble() * Math.PI * 2;
					int color = primal ? palette[RANDOM.nextInt(2)] : rainbow(RANDOM.nextFloat());
					level.addParticle(dust(color, 2.2f), c.x + Math.cos(angle) * r * 1.2, c.y - r, c.z + Math.sin(angle) * r * 1.2,
							0, 0.12, 0);
				}
			}
		}
		if (!climaxDone && t >= MEGA_SHATTER) {
			climax();
		}
		if (t >= MEGA_SHATTER + 0.1f && t < MEGA_SHATTER + 1.4f) {
			for (int i = 0; i < 3; i++) {
				double angle = RANDOM.nextDouble() * Math.PI * 2;
				level.addParticle(primal ? dust(palette[RANDOM.nextInt(2)], 1.2f) : dust(0xFFE070, 1.2f),
						c.x + Math.cos(angle) * r, c.y - r + RANDOM.nextDouble() * r * 2, c.z + Math.sin(angle) * r, 0, 0.05, 0);
			}
		}
	}

	private static void tickZMove(ClientLevel level, float t) {
		Vec3 c = center();
		Vec3 feet = entity.position();
		double r = radius();
		cue("start", t >= 0f, SoundEvents.BEACON_POWER_SELECT, 1.5f, 1f);
		cue("charge1", t >= 0.3f, SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.8f, 1f);
		cue("charge2", t >= 0.9f, SoundEvents.RESPAWN_ANCHOR_CHARGE, 1.0f, 1f);
		cue("charge3", t >= 1.5f, SoundEvents.RESPAWN_ANCHOR_CHARGE, 1.3f, 1f);
		cue("spin", t >= 1.9f, SoundEvents.ELYTRA_FLYING, 1.8f, 0.6f);
		if (t >= 0.2f && t < Z_RELEASE) {
			// Golden aura rising, rings climbing round the Pokémon.
			for (int i = 0; i < 5; i++) {
				double angle = RANDOM.nextDouble() * Math.PI * 2;
				double rr = r * (0.8 + 0.5 * RANDOM.nextDouble());
				level.addParticle(dust(RANDOM.nextBoolean() ? 0xFFC020 : 0xFFF080, 1.3f),
						feet.x + Math.cos(angle) * rr, feet.y + RANDOM.nextDouble() * r * 0.5, feet.z + Math.sin(angle) * rr, 0, 0.18, 0);
			}
			if (RANDOM.nextBoolean()) {
				level.addParticle(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, (RANDOM.nextDouble() - 0.5) * 0.4, 0.2, (RANDOM.nextDouble() - 0.5) * 0.4);
			}
			double ringHeight = ((t * 1.6) % 1.0) * r * 3;
			for (int k = 0; k < 14; k++) {
				double angle = k * Math.PI * 2 / 14 + t * 4;
				level.addParticle(dust(0xFFD040, 1.0f), feet.x + Math.cos(angle) * r * 1.4, feet.y + ringHeight, feet.z + Math.sin(angle) * r * 1.4,
						0, 0, 0);
			}
			if (t >= 1.0f) {
				level.addParticle(ParticleTypes.END_ROD, c.x + (RANDOM.nextDouble() - 0.5) * r, feet.y, c.z + (RANDOM.nextDouble() - 0.5) * r,
						0, 0.25, 0);
			}
		}
		if (!climaxDone && t >= Z_RELEASE) {
			climax();
		}
	}

	private static void tickTera(ClientLevel level, float t) {
		Vec3 c = center();
		double r = radius();
		int rgb = color();
		cue("start", t >= 0f, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1f);
		cue("grow1", t >= 0.5f, SoundEvents.AMETHYST_CLUSTER_PLACE, 0.8f, 1f);
		cue("grow2", t >= 0.9f, SoundEvents.AMETHYST_CLUSTER_PLACE, 1.0f, 1f);
		cue("grow3", t >= 1.3f, SoundEvents.AMETHYST_CLUSTER_PLACE, 1.2f, 1f);
		cue("grow4", t >= 1.7f, SoundEvents.AMETHYST_CLUSTER_PLACE, 1.5f, 1f);
		cue("crack", t >= 2.0f, SoundEvents.AMETHYST_BLOCK_BREAK, 1.4f, 1f);
		if (t >= 0.4f && t < 2.0f) {
			// Crystal chips spiralling in.
			for (int i = 0; i < 5; i++) {
				double angle = RANDOM.nextDouble() * Math.PI * 2;
				double rr = r * 3.2;
				Vec3 from = new Vec3(c.x + Math.cos(angle) * rr, c.y + (RANDOM.nextDouble() - 0.4) * r * 2, c.z + Math.sin(angle) * rr);
				Vec3 inward = c.subtract(from).scale(0.06);
				Vec3 swirl = new Vec3(-Math.sin(angle), 0, Math.cos(angle)).scale(0.15);
				level.addParticle(i % 2 == 0 ? shard(teraType) : dust(rgb, 1.5f), from.x, from.y, from.z,
						inward.x + swirl.x, inward.y, inward.z + swirl.z);
			}
		}
		if (!climaxDone && t >= TERA_SHATTER) {
			climax();
		}
	}

	/** The climax: the model switches (Mega / Primal), the Z-Power is released, the crystal shatters. */
	private static void climax() {
		climaxDone = true;
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null || entity == null || entity.isRemoved()) {
			return;
		}
		Vec3 c = center();
		double r = radius();
		level.addParticle(ParticleTypes.FLASH, c.x, c.y, c.z, 0, 0, 0);
		switch (kind) {
			case MEGA, PRIMAL -> {
				if (change != null) {
					Set<String> aspects = new HashSet<>(entity.getEntityData().get(PokemonEntity.Companion.getASPECTS()));
					aspects.add(change.aspect());
					entity.getEntityData().set(PokemonEntity.Companion.getASPECTS(), aspects);
				}
				boolean primal = kind == Kind.PRIMAL;
				burst(level, c, r, 70, ParticleTypes.END_ROD, 0.35);
				burst(level, c, r, 40, ParticleTypes.FIREWORK, 0.45);
				for (int i = 0; i < 40; i++) {
					Vec3 v = randomUnit().scale(0.5);
					int color = primal ? ("kyogre".equals(species()) ? 0x50A0FF : 0xFF5020) : rainbow(i / 40f);
					level.addParticle(dust(color, 2.5f), c.x, c.y, c.z, v.x, v.y, v.z);
				}
				BattleCinematic.play(SoundEvents.GLASS_BREAK, 0.8f, 1f);
				BattleCinematic.play(SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 0.9f, 1f);
				BattleCinematic.play(SoundEvents.TOTEM_USE, primal ? 0.7f : 1.2f, 0.6f);
			}
			case Z_MOVE -> {
				burst(level, c, r, 80, ParticleTypes.END_ROD, 0.6);
				for (int i = 0; i < 50; i++) {
					Vec3 v = randomUnit().scale(0.6);
					level.addParticle(dust(i % 2 == 0 ? 0xFFC020 : 0xFFFFFF, 2.0f), c.x, c.y, c.z, v.x, v.y, v.z);
				}
				Vec3 feet = entity.position();
				for (int i = 0; i < 48; i++) {
					double angle = i * Math.PI * 2 / 48;
					double vx = Math.cos(angle) * 0.6;
					double vz = Math.sin(angle) * 0.6;
					level.addParticle(ParticleTypes.CLOUD, feet.x, feet.y + 0.1, feet.z, vx, 0.01, vz);
					level.addParticle(dust(0xFFD040, 2.0f), feet.x, feet.y + 0.2, feet.z, vx * 1.3, 0.02, vz * 1.3);
				}
				BattleCinematic.play(SoundEvents.WARDEN_SONIC_BOOM, 1.3f, 0.7f);
				BattleCinematic.play(SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 0.7f, 1f);
				BattleCinematic.play(SoundEvents.LIGHTNING_BOLT_THUNDER, 1.6f, 0.4f);
			}
			case TERA -> {
				for (int i = 0; i < 60; i++) {
					Vec3 v = randomUnit().scale(0.45);
					level.addParticle(shard(teraType), c.x + v.x * r, c.y + v.y * r, c.z + v.z * r, v.x, v.y + 0.1, v.z);
				}
				for (int i = 0; i < 40; i++) {
					Vec3 v = randomUnit().scale(0.4);
					level.addParticle(dust(color(), 2.2f), c.x, c.y, c.z, v.x, v.y, v.z);
				}
				burst(level, c, r, 30, ParticleTypes.END_ROD, 0.3);
				BattleCinematic.play(SoundEvents.GLASS_BREAK, 1.0f, 1f);
				BattleCinematic.play(SoundEvents.AMETHYST_BLOCK_BREAK, 0.6f, 1f);
				BattleCinematic.play(SoundEvents.BEACON_ACTIVATE, 1.6f, 0.8f);
				glowing.put(entity.getId(), teraType);
				if (change == null) {
					glowUntil.put(entity.getId(), System.currentTimeMillis() + 12_000);
				}
			}
		}
	}

	private static void cue(String id, boolean due, SoundEvent sound, float pitch, float volume) {
		if (!due || !cues.add(id)) {
			return;
		}
		if (sound == null) {
			// The cry.
			CobblemonPackets.dispatchLocally(new PlayPosableAnimationPacket(entity.getId(), Set.of("cry"), List.of()));
			return;
		}
		BattleCinematic.play(sound, pitch, volume);
	}

	private static void burst(ClientLevel level, Vec3 c, double r, int count, ParticleOptions particle, double speed) {
		for (int i = 0; i < count; i++) {
			Vec3 v = randomUnit().scale(speed * (0.6 + 0.6 * RANDOM.nextDouble()));
			level.addParticle(particle, c.x, c.y, c.z, v.x, v.y, v.z);
		}
	}

	// =====================================================================
	// Camera (CameraMixin, GameRendererFovMixin)
	// =====================================================================

	/**
	 * The camera this frame while a set piece plays, else null. {@code base} is where it would be otherwise (the
	 * director's shot, or the player's own view): the set piece blends in from it and back to it.
	 */
	public static BattleCinematic.CameraPose cameraPose(Camera camera, BattleCinematic.CameraPose base, float partialTick) {
		if (!playing()) {
			return null;
		}
		float t = time();
		BattleCinematic.CameraPose shot = switch (kind) {
			case MEGA, PRIMAL -> megaShot(t, partialTick);
			case Z_MOVE -> zShot(t, partialTick);
			case TERA -> teraShot(t, partialTick);
		};
		float in = kind == Kind.Z_MOVE ? 0.35f : 0.45f;
		float out = 0.6f;
		if (t < in) {
			return blend(base, shot, smooth(t / in));
		}
		if (t > kind.seconds - out) {
			return blend(shot, base, smooth((t - (kind.seconds - out)) / out));
		}
		return shot;
	}

	/** Field of view multiplier: the Z-Move's dolly zoom, the punches at each climax. */
	public static double fovScale() {
		if (!playing()) {
			return 1.0;
		}
		float t = time();
		return switch (kind) {
			case Z_MOVE -> {
				if (t < 0.35f) {
					yield 1.0;
				}
				if (t < 1.9f) {
					yield Mth.lerp(smooth((t - 0.35f) / 1.55f), 1.0f, 1.35f);
				}
				if (t < Z_RELEASE) {
					yield Mth.lerp(smooth((t - 1.9f) / 0.7f), 1.35f, 1.15f);
				}
				yield punch(t - Z_RELEASE, 0.72f, Math.max(0f, 1f - (t - (kind.seconds - 0.5f)) / 0.5f) * 0.15f + 1f);
			}
			case MEGA, PRIMAL -> t < MEGA_SHATTER ? 1.0 : punch(t - MEGA_SHATTER, 0.85f, 1f);
			case TERA -> t < TERA_SHATTER ? 1.0 : punch(t - TERA_SHATTER, 0.85f, 1f);
		};
	}

	/** From {@code low} back to {@code rest} in half a second, overshooting a little. */
	private static double punch(float since, float low, float rest) {
		if (since >= 0.6f) {
			return rest;
		}
		float k = since / 0.6f;
		float spring = 1f - (float) Math.exp(-6 * k) * (float) Math.cos(k * 9);
		return Mth.lerp(spring, low, rest);
	}

	private static BattleCinematic.CameraPose megaShot(float t, float pt) {
		double r = radius();
		if (t < MEGA_SHATTER) {
			float k = t / MEGA_SHATTER;
			float shake = t > 2.9f ? 0.03f + 0.1f * (t - 2.9f) / 0.4f : 0f;
			return orbit(pt, -20f + 40f * smooth(k), 1.0 - 0.25 * k, r * 0.3, 0, shake, t);
		}
		float k = Math.min(1f, (t - MEGA_SHATTER) / 1.1f);
		float shake = 0.25f * (float) Math.exp(-(t - MEGA_SHATTER) * 6);
		// Pulled back by the blast, then looking up at the new form.
		return orbit(pt, 15f - 15f * smooth(k), Mth.lerp(easeOut(Math.min(1f, (t - MEGA_SHATTER) / 0.4f)), 0.7f, 1.35f), -r * 0.25, r * 0.25,
				shake, t);
	}

	private static BattleCinematic.CameraPose zShot(float t, float pt) {
		double r = radius();
		if (t < 1.9f) {
			float k = smooth(Math.max(0f, t - 0.35f) / 1.55f);
			return orbit(pt, Mth.lerp(k, 140f, 100f), Mth.lerp(k, 2.2f, 0.9f), r * 0.2, 0, 0f, t);
		}
		if (t < Z_RELEASE) {
			float k = smooth((t - 1.9f) / 0.7f);
			return orbit(pt, Mth.lerp(k, 100f, -20f), 0.9, Mth.lerp(k, r * 0.1, r * 0.4), 0, 0.03f, t);
		}
		float k = easeOut(Math.min(1f, (t - Z_RELEASE) / 0.6f));
		return orbit(pt, -20f, Mth.lerp(k, 0.9f, 1.6f), r * 0.4, 0, 0.3f * (float) Math.exp(-(t - Z_RELEASE) * 5), t);
	}

	private static BattleCinematic.CameraPose teraShot(float t, float pt) {
		double r = radius();
		if (t < 2.0f) {
			float k = smooth(t / 2.0f);
			return orbit(pt, Mth.lerp(k, -15f, 25f), Mth.lerp(k, 1.0f, 0.85f), r * 0.5, 0, 0f, t);
		}
		if (t < TERA_SHATTER) {
			float k = (t - 2.0f) / 0.3f;
			return orbit(pt, 25f, Mth.lerp(k, 0.85f, 0.7f), r * 0.5, 0, 0.05f + 0.1f * k, t);
		}
		float k = easeOut(Math.min(1f, (t - TERA_SHATTER) / 0.4f));
		float slow = Math.min(1f, (t - TERA_SHATTER) / 1.5f);
		return orbit(pt, 25f + 10f * slow, Mth.lerp(k, 0.7f, 1.3f), Mth.lerp(k, r * 0.5, -r * 0.1), r * 0.15,
				0.2f * (float) Math.exp(-(t - TERA_SHATTER) * 6), t);
	}

	/**
	 * In front of the Pokémon ({@code angle} 0 = right in front, degrees round it), {@code distance} times its
	 * framing distance away, {@code height} above its centre, looking at its centre raised by {@code lookUp}.
	 */
	private static BattleCinematic.CameraPose orbit(float pt, float angle, double distance, double height, double lookUp, float shake, float t) {
		Vec3 c = entity.getPosition(pt).add(0, entity.getBbHeight() / 2.0, 0);
		Vec3 forward = Vec3.directionFromRotation(0, entity.getYRot() + angle);
		double frame = Mth.clamp(radius() * 2.6 + 1.6, 2.6, 14.0) * distance;
		Vec3 position = c.add(forward.scale(frame)).add(0, height, 0);
		if (shake > 0f) {
			double s = shake * Math.max(1.0, radius());
			position = position.add(Math.sin(t * 71) * s, Math.sin(t * 53 + 1) * s, Math.cos(t * 67) * s);
		}
		Vec3 d = c.add(0, lookUp, 0).subtract(position);
		float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
		return new BattleCinematic.CameraPose(position, yaw, pitch, true);
	}

	private static BattleCinematic.CameraPose blend(BattleCinematic.CameraPose from, BattleCinematic.CameraPose to, float k) {
		return new BattleCinematic.CameraPose(from.position().lerp(to.position(), k), Mth.rotLerp(k, from.yaw(), to.yaw()),
				Mth.lerp(k, from.pitch(), to.pitch()), true);
	}

	// =====================================================================
	// Helpers
	// =====================================================================

	private static Vec3 center() {
		return entity.position().add(0, entity.getBbHeight() / 2.0, 0);
	}

	/** Half the Pokémon's larger dimension, at least half a block. */
	private static double radius() {
		return Math.max(0.5, Math.max(entity.getBbHeight(), entity.getBbWidth()) / 2.0);
	}

	private static Vec3 randomUnit() {
		double z = RANDOM.nextDouble() * 2 - 1;
		double a = RANDOM.nextDouble() * Math.PI * 2;
		double s = Math.sqrt(1 - z * z);
		return new Vec3(Math.cos(a) * s, z, Math.sin(a) * s);
	}

	static float smooth(float k) {
		k = Mth.clamp(k, 0f, 1f);
		return k * k * (3 - 2 * k);
	}

	static float easeOut(float k) {
		k = Mth.clamp(k, 0f, 1f);
		return 1f - (1f - k) * (1f - k) * (1f - k);
	}

	private static DustParticleOptions dust(int rgb, float size) {
		return new DustParticleOptions(new Vector3f(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f), size);
	}

	/** A crystal chip: a stained glass fragment of the Tera type's colour. */
	private static BlockParticleOption shard(String type) {
		return new BlockParticleOption(ParticleTypes.BLOCK, glass(type).defaultBlockState());
	}

	private static Block glass(String type) {
		return switch (type == null ? "" : type) {
			case "fire" -> Blocks.ORANGE_STAINED_GLASS;
			case "water", "dragon" -> Blocks.BLUE_STAINED_GLASS;
			case "grass", "bug" -> Blocks.LIME_STAINED_GLASS;
			case "electric" -> Blocks.YELLOW_STAINED_GLASS;
			case "ice", "flying" -> Blocks.LIGHT_BLUE_STAINED_GLASS;
			case "fighting" -> Blocks.RED_STAINED_GLASS;
			case "poison", "ghost" -> Blocks.PURPLE_STAINED_GLASS;
			case "ground", "rock" -> Blocks.BROWN_STAINED_GLASS;
			case "psychic", "fairy" -> Blocks.PINK_STAINED_GLASS;
			case "dark" -> Blocks.BLACK_STAINED_GLASS;
			case "steel" -> Blocks.LIGHT_GRAY_STAINED_GLASS;
			default -> Blocks.WHITE_STAINED_GLASS;
		};
	}

	/** Each Tera type's colour (the games' type colours); Stellar cycles through the rainbow. */
	public static int teraColor(String type) {
		return switch (type == null ? "" : type) {
			case "normal" -> 0xC6C6A7;
			case "fire" -> 0xFF7F24;
			case "water" -> 0x4F8BFF;
			case "grass" -> 0x6CD640;
			case "electric" -> 0xFFD81F;
			case "ice" -> 0x8FE8E8;
			case "fighting" -> 0xE0302A;
			case "poison" -> 0xB044B8;
			case "ground" -> 0xE8C060;
			case "flying" -> 0xA890FF;
			case "psychic" -> 0xFF5590;
			case "bug" -> 0xA8C820;
			case "rock" -> 0xC8A840;
			case "ghost" -> 0x8060B8;
			case "dragon" -> 0x7038FF;
			case "dark" -> 0x6E5848;
			case "steel" -> 0xB8B8D8;
			case "fairy" -> 0xFF9AD8;
			default -> rainbow((System.currentTimeMillis() % 3000) / 3000f);
		};
	}

	/** A bright colour along the rainbow, {@code k} in [0, 1). */
	static int rainbow(float k) {
		return Mth.hsvToRgb(k - (float) Math.floor(k), 0.75f, 1f) & 0xFFFFFF;
	}

	static int mix(int a, int b, float k) {
		int r = Math.round(Mth.lerp(k, (a >> 16) & 0xFF, (b >> 16) & 0xFF));
		int g = Math.round(Mth.lerp(k, (a >> 8) & 0xFF, (b >> 8) & 0xFF));
		int bl = Math.round(Mth.lerp(k, a & 0xFF, b & 0xFF));
		return (r << 16) | (g << 8) | bl;
	}
}
