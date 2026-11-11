package com.mystaria.phantasmon.client.pokemon;

import org.joml.Vector3f;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

import com.mystaria.phantasmon.client.ghost.PhantasmonEntities;

/**
 * The auras and the size of a Pokémon's look (TODO-37), on Phantasmon's own entities (Ghosts, Ghost battle
 * send-outs, hub Ghosts). Auras are Minecraft particles around the body, sized by it — they work the same with or
 * without shaders. The size is the entity's {@code scale} attribute, which Minecraft's renderer applies. Client thread.
 */
public final class LookAuras {

	private static final Vector3f GOLD = new Vector3f(1.0f, 0.82f, 0.18f);
	private static final Vector3f PALE_GOLD = new Vector3f(1.0f, 0.97f, 0.6f);
	private static final Vector3f ELECTRIC = new Vector3f(0.55f, 0.85f, 1.0f);
	private static final Vector3f ELECTRIC_WHITE = new Vector3f(0.95f, 0.98f, 1.0f);
	private static int ticks;

	private LookAuras() {
	}

	/** The look's size on {@code entity} (50 to 200 %), through Minecraft's scale attribute. */
	public static void applyScale(PokemonEntity entity, PokemonLook look) {
		if (look.scale() == 100) {
			return;
		}
		AttributeInstance scale = entity.getAttribute(Attributes.SCALE);
		if (scale != null) {
			scale.setBaseValue(Math.max(PokemonLook.SCALE_MIN, Math.min(PokemonLook.SCALE_MAX, look.scale())) / 100.0);
		}
	}

	/** Every client tick: each aura's particles around its Pokémon. */
	public static void tick() {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null || mc.isPaused()) {
			return;
		}
		ticks++;
		for (PokemonEntity entity : PhantasmonEntities.all()) {
			if (entity.isRemoved() || entity.level() != level || entity.isInvisible()) {
				continue;
			}
			PokemonLook look = PokemonLook.fromAspects(entity.getAspects());
			if (look.aura() == null) {
				continue;
			}
			switch (look.aura()) {
				case "super_saiyan" -> superSaiyan(level, entity, level.getRandom());
				case "electric" -> electric(level, entity, level.getRandom());
				default -> {
				}
			}
		}
	}

	/** Golden flames rising along the body, white sparks climbing, now and then a crackle. */
	private static void superSaiyan(ClientLevel level, PokemonEntity entity, RandomSource random) {
		float width = entity.getBbWidth();
		float height = entity.getBbHeight();
		int count = 2 + Math.round(width * 2);
		for (int i = 0; i < count; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double radius = width * (0.45 + random.nextDouble() * 0.25);
			double x = entity.getX() + Math.cos(angle) * radius;
			double z = entity.getZ() + Math.sin(angle) * radius;
			double y = entity.getY() + random.nextDouble() * height * 0.9;
			float size = 0.8f + random.nextFloat() * 0.9f;
			level.addParticle(new DustColorTransitionOptions(GOLD, PALE_GOLD, size), x, y, z, 0, 0.06, 0);
		}
		if (ticks % 2 == 0) {
			double angle = random.nextDouble() * Math.PI * 2;
			level.addParticle(ParticleTypes.END_ROD, entity.getX() + Math.cos(angle) * width * 0.5, entity.getY() + random.nextDouble() * 0.3,
					entity.getZ() + Math.sin(angle) * width * 0.5, 0, 0.08 + random.nextDouble() * 0.06, 0);
		}
		if (random.nextInt(12) == 0) {
			level.addParticle(ParticleTypes.ELECTRIC_SPARK, entity.getX() + (random.nextDouble() - 0.5) * width,
					entity.getY() + random.nextDouble() * height, entity.getZ() + (random.nextDouble() - 0.5) * width, 0, 0, 0);
		}
	}

	/** Sparks crackling over the body, a few blue-white glints. */
	private static void electric(ClientLevel level, PokemonEntity entity, RandomSource random) {
		float width = entity.getBbWidth();
		float height = entity.getBbHeight();
		int count = 1 + Math.round(width * 1.5f);
		for (int i = 0; i < count; i++) {
			double x = entity.getX() + (random.nextDouble() - 0.5) * width * 1.3;
			double y = entity.getY() + random.nextDouble() * height;
			double z = entity.getZ() + (random.nextDouble() - 0.5) * width * 1.3;
			level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z, (random.nextDouble() - 0.5) * 0.2, (random.nextDouble() - 0.5) * 0.2,
					(random.nextDouble() - 0.5) * 0.2);
		}
		if (ticks % 3 == 0) {
			double x = entity.getX() + (random.nextDouble() - 0.5) * width;
			double y = entity.getY() + random.nextDouble() * height;
			double z = entity.getZ() + (random.nextDouble() - 0.5) * width;
			level.addParticle(new DustParticleOptions(random.nextBoolean() ? ELECTRIC : ELECTRIC_WHITE, 1.0f), x, y, z, 0, 0, 0);
		}
	}
}
