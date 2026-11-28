package com.mystaria.phantasmon.client.gui;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.client.gui.PokemonGuiUtilsKt;
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel;
import com.cobblemon.mod.common.client.render.models.blockbench.animation.PoseAnimation;
import com.cobblemon.mod.common.client.render.models.blockbench.bedrock.animation.BedrockPoseAnimation;
import com.cobblemon.mod.common.client.render.models.blockbench.pose.Pose;
import com.cobblemon.mod.common.pokemon.RenderablePokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.cobblemon.mod.common.util.math.QuaternionUtilsKt;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.mystaria.phantasmon.client.pokemon.CobblemonHeldItems;
import com.mystaria.phantasmon.client.pokemon.PokemonDto;
import com.mystaria.phantasmon.client.pokemon.PokemonGender;

/**
 * Cobblemon model/item drawing shared by every Phantasmon screen (PC, trade).
 * Extracted from {@link PhantasmonPcScreen} as-is when the trade screen
 * needed the exact same live 3D miniatures — see that class's javadoc for how
 * the {@code drawProfilePokemon} call shape was reverse-engineered.
 */
public final class PokemonGuiRendering {

	private static final Logger LOG = LoggerFactory.getLogger(PokemonGuiRendering.class);
	private static java.lang.reflect.Method drawProfilePokemonDefaultMethod;

	/** Cobblemon's own {@code StorageSlot.renderSlot} inner scale argument (decompiled, never changed — size is tuned via the outer pose scale only). */
	private static final float ICON_INNER_SCALE = 4.5f;

	private PokemonGuiRendering() {
	}

	// ---- Idle animation (2026-11-28) ----

	/** A model drawn alive: Cobblemon's animation state kept from frame to frame, and whether it has an idle animation. */
	private static final class Animated {
		final SilentGuiState state = new SilentGuiState();
		final float phase;
		long lastNanos;
		Boolean hasIdle;

		Animated(String key) {
			this.phase = (key.hashCode() & 1023) / 1023f * (float) (Math.PI * 2);
		}
	}

	private static final int MAX_ANIMATED = 64;
	private static final Map<String, Animated> ANIMATED = new LinkedHashMap<>(MAX_ANIMATED, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Animated> eldest) {
			return size() > MAX_ANIMATED;
		}
	};
	private static String animationScope;
	private static String pendingCry;

	/**
	 * From here to {@link #endAnimated()}, the models drawn play their idle animation (one state per spot on screen,
	 * {@code scope} telling screens apart); those without one bob gently up and down instead.
	 */
	public static void beginAnimated(String scope) {
		animationScope = scope;
	}

	public static void endAnimated() {
		animationScope = null;
	}

	/** The animated model of {@code speciesId} drawn at {@code centerX} plays its cry animation once, next frame. */
	public static void playCry(String speciesId, float centerX) {
		pendingCry = speciesId == null ? null : speciesId.toLowerCase(Locale.ROOT) + "@" + Math.round(centerX);
	}

	/** The live state of the model about to be drawn, advanced by the time since its last frame (null when not animating). */
	private static Animated animated(String speciesId, Set<String> aspects, float centerX, float anchorY) {
		if (animationScope == null) {
			return null;
		}
		String key = animationScope + "|" + Math.round(centerX) + "|" + Math.round(anchorY) + "|" + speciesId + "|"
				+ new java.util.TreeSet<>(aspects);
		Animated anim = ANIMATED.computeIfAbsent(key, Animated::new);
		String cry = speciesId.toLowerCase(Locale.ROOT) + "@" + Math.round(centerX);
		if (cry.equals(pendingCry)) {
			pendingCry = null;
			anim.state.getActiveAnimations().clear();
			anim.state.addFirstAnimation(Set.of("cry"));
		}
		return anim;
	}

	/** Ticks elapsed since this model's last frame (FloatingState adds them up: call once per frame). */
	private static float advance(Animated anim) {
		long now = System.nanoTime();
		float ticks = anim.lastNanos == 0 ? 0f : Math.min(5f, (now - anim.lastNanos) / 50_000_000f);
		anim.lastNanos = now;
		return ticks;
	}

	/** After the first frame: does the pose Cobblemon picked play a real (Bedrock) animation? */
	private static void learnIdle(Animated anim) {
		if (anim.hasIdle != null) {
			return;
		}
		PosableModel model = anim.state.getCurrentModel();
		String poseName = anim.state.getCurrentPose();
		if (model == null || poseName == null) {
			return;
		}
		Pose pose = model.getPose(poseName);
		boolean idle = false;
		if (pose != null) {
			for (PoseAnimation animation : pose.getAnimations()) {
				if (animation instanceof BedrockPoseAnimation) {
					idle = true;
					break;
				}
			}
		}
		anim.hasIdle = idle;
	}

	/**
	 * Draws a live 3D miniature via Cobblemon's {@code drawProfilePokemon}. That
	 * Kotlin function has 9 defaulted parameters (mask {@code 65416}, verified
	 * by decompiling Cobblemon's own {@code StorageSlot.renderSlot}). The
	 * generated {@code drawProfilePokemon$default} bridge that lets a caller
	 * omit them is marked {@code @JvmSynthetic}, which the Java compiler
	 * enforces by hiding it from ordinary method calls even though it's a
	 * public bytecode-level method — so it's invoked here via reflection
	 * instead, which isn't subject to that source-level filter.
	 *
	 * <p>{@code outerScale} replicates Cobblemon's own {@code pose.scale(2.5f,
	 * 2.5f, 1f)} wrapping call (see {@code PhantasmonPcScreen.COBBLEMON_OUTER_SCALE}
	 * for why this — not the inner scale argument, and not the translate z —
	 * is what keeps the model centered). The model hangs mostly <em>below</em>
	 * {@code (centerX, anchorY)}.
	 */
	public static void renderModel(GuiGraphics graphics, String speciesId, String form, boolean shiny, Object storedGender,
			float centerX, float anchorY, float outerScale) {
		renderModel(graphics, speciesId, form, shiny, storedGender, centerX, anchorY, outerScale, null);
	}

	/** Same, wearing {@code look} (TODO-37): its filter's aspect makes Cobblemon draw the recoloured texture. */
	public static void renderModel(GuiGraphics graphics, String speciesId, String form, boolean shiny, Object storedGender,
			float centerX, float anchorY, float outerScale, com.mystaria.phantasmon.client.pokemon.PokemonLook look) {
		Species species = PokemonSpecies.INSTANCE.getByName(speciesId);
		if (species == null) {
			return;
		}
		// Form models are driven by the form's Cobblemon *aspects* (e.g. Arceus Fairy = "fairy-plate",
		// Ogerpon Wellspring = "wellspring-mask"), not by the form name — passing the name drew the base form.
		Set<String> aspects = new HashSet<>(formAspects(species, form));
		if (shiny) {
			aspects.add("shiny");
		}
		String genderAspect = genderAspect(species, form, storedGender);
		if (genderAspect != null) {
			aspects.add(genderAspect);
		}
		if (look != null) {
			aspects.addAll(look.aspects());
		}
		RenderablePokemon renderable = new RenderablePokemon(species, aspects, ItemStack.EMPTY);
		Animated anim = animated(speciesId, aspects, centerX, anchorY);
		com.cobblemon.mod.common.client.render.models.blockbench.PosableState state = anim == null ? new FloatingState() : anim.state;
		float ticks = anim == null ? 0f : advance(anim);
		if (anim != null && Boolean.FALSE.equals(anim.hasIdle)) {
			// No idle animation: a slow, slight breathing bob instead.
			anchorY += (float) Math.sin(System.nanoTime() / 1e9 * (Math.PI * 2 / 2.8) + anim.phase) * 1.5f;
		}

		PoseStack poseStack = graphics.pose();
		poseStack.pushPose();
		try {
			poseStack.translate(centerX, anchorY, 0);
			poseStack.scale(outerScale, outerScale, 1f);
			Quaternionf rotation = QuaternionUtilsKt.fromEulerXYZDegrees(new Quaternionf(), new Vector3f(13.0f, 35.0f, 0f));
			drawProfilePokemonDefault().invoke(null,
					renderable,
					poseStack,
					rotation,
					null,
					state,
					ticks,
					ICON_INNER_SCALE,
					null,
					false,
					0f, 0f, 0f, 0f, 0f, 0f,
					0,
					65416,
					null);
			if (anim != null) {
				learnIdle(anim);
			}
			// The look's glowing shell (TODO-37): the model once more, in the shell's moving additive light.
			com.mystaria.phantasmon.client.pokemon.LookShells.Shell shell = com.mystaria.phantasmon.client.pokemon.LookShells.of(look);
			if (shell != null) {
				com.mystaria.phantasmon.client.pokemon.LookTextures.guiShell(shell);
				try {
					drawProfilePokemonDefault().invoke(null, renderable, poseStack, rotation, null, anim == null ? new FloatingState() : state, 0f,
							ICON_INNER_SCALE, null, false, 0f, 0f, 0f, 0f, 0f, 0f, 0, 65416, null);
					graphics.flush();
				} finally {
					com.mystaria.phantasmon.client.pokemon.LookTextures.guiShell(null);
				}
			}
		} catch (Exception ex) {
			// Anything here (reflection failure, a bad argument, a Cobblemon-side
			// rendering error) must not propagate: an unbalanced pushPose/popPose
			// would corrupt every remaining draw call for the rest of this frame,
			// potentially blanking the whole screen instead of just this one icon.
			LOG.warn("Failed to render Pokémon model for {} (Cobblemon API mismatch?)", speciesId, ex);
		} finally {
			poseStack.popPose();
		}
	}

	/**
	 * The species' form matching a stored form id, tolerant to how it was written (Showdown import stores
	 * e.g. "fairy", "wellspringtera"; Cobblemon names are "Fairy", "Wellspring-Tera"): compares names and
	 * Showdown ids with every non-alphanumeric character stripped. {@code null} for the base form / no match.
	 */
	public static com.cobblemon.mod.common.pokemon.FormData resolveForm(Species species, String formId) {
		if (species == null || formId == null || formId.isBlank()) {
			return null;
		}
		String wanted = normalize(formId);
		for (com.cobblemon.mod.common.pokemon.FormData candidate : species.getForms()) {
			if (wanted.equals(normalize(candidate.getName())) || wanted.equals(normalize(candidate.formOnlyShowdownId()))) {
				return candidate;
			}
		}
		return null;
	}

	/** The raw {@code data.gender} of a Pokémon ("M"/"F"/absent), as {@link #genderAspect} expects it. */
	public static Object storedGender(PokemonDto pokemon) {
		return pokemon.data() == null ? null : pokemon.data().get("gender");
	}

	/**
	 * Cobblemon's gender aspect ("male"/"female"/"genderless") for models that differ by gender (Meowstic,
	 * Pikachu...): the stored gender, else whatever the species' ratio settles; {@code null} when both genders
	 * exist and none is stored (the base model). Renderers that pass explicit aspects (the GUI, the Ghost
	 * entity) never get Cobblemon's own gender provider, so it has to be added by hand.
	 */
	public static String genderAspect(Species species, String formId, Object storedGender) {
		com.cobblemon.mod.common.pokemon.FormData form = resolveForm(species, formId);
		float ratio = form != null ? form.getMaleRatio() : species.getMaleRatio();
		return switch (PokemonGender.resolve(storedGender, ratio)) {
			case MALE -> "male";
			case FEMALE -> "female";
			case GENDERLESS -> "genderless";
			case UNKNOWN -> null;
		};
	}

	/** Aspects that make Cobblemon render {@code formId}; falls back to the raw id (old behavior) if the form is unknown. */
	public static Set<String> formAspects(Species species, String formId) {
		Set<String> aspects = new HashSet<>();
		com.cobblemon.mod.common.pokemon.FormData form = resolveForm(species, formId);
		if (form != null) {
			aspects.addAll(form.getAspects());
		} else if (formId != null && !formId.isBlank()) {
			aspects.add(formId.toLowerCase(Locale.ROOT));
		}
		return aspects;
	}

	private static String normalize(String value) {
		return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
	}

	private static java.lang.reflect.Method drawProfilePokemonDefault() {
		java.lang.reflect.Method cached = drawProfilePokemonDefaultMethod;
		if (cached != null) {
			return cached;
		}
		for (java.lang.reflect.Method candidate : PokemonGuiUtilsKt.class.getMethods()) {
			if (candidate.getName().equals("drawProfilePokemon$default")
					&& candidate.getParameterCount() == 18
					&& candidate.getParameterTypes()[0] == RenderablePokemon.class) {
				candidate.setAccessible(true);
				drawProfilePokemonDefaultMethod = candidate;
				return candidate;
			}
		}
		throw new IllegalStateException("Cobblemon's PokemonGuiUtilsKt.drawProfilePokemon$default not found (API changed?)");
	}

	/** Resolves a held item id (e.g. "choice_band") to its real Cobblemon-registered {@link ItemStack} for icon rendering, via {@link CobblemonHeldItems} — the same "has a battle effect" item set the edit screen's picker offers, not just any {@code cobblemon:}-namespaced item; {@link ItemStack#EMPTY} if unresolvable. */
	public static ItemStack heldItemStack(String heldItemId) {
		if (heldItemId == null || heldItemId.isBlank()) {
			return ItemStack.EMPTY;
		}
		Item item = CobblemonHeldItems.resolve(heldItemId);
		return item == null ? ItemStack.EMPTY : new ItemStack(item);
	}

	/** Vanilla item icons always render at a fixed 16×16 — scaled here via pose to the requested size. */
	/**
	 * A held item's icon: its Minecraft item, or Phantasmon's own icon for an item Cobblemon doesn't have.
	 * Returns whether something was drawn.
	 */
	public static boolean renderHeldItem(GuiGraphics graphics, String heldItemId, float x, float y, float size) {
		ItemStack stack = heldItemStack(heldItemId);
		if (!stack.isEmpty()) {
			renderItemIcon(graphics, stack, x, y, size);
			return true;
		}
		net.minecraft.resources.ResourceLocation icon = com.mystaria.phantasmon.client.pokemon.VirtualItems.icon(heldItemId);
		if (icon != null) {
			PoseStack pose = graphics.pose();
			pose.pushPose();
			pose.translate(x, y, 0);
			pose.scale(size / 32f, size / 32f, 1f);
			com.mojang.blaze3d.systems.RenderSystem.enableBlend();
			graphics.blit(icon, 0, 0, 0, 0, 32, 32, 32, 32);
			pose.popPose();
			return true;
		}
		return false;
	}

	/** A held item's display name: its Minecraft item's, a virtual item's, else the raw id; null if none. */
	public static String heldItemName(String heldItemId) {
		if (heldItemId == null || heldItemId.isBlank()) {
			return null;
		}
		ItemStack stack = heldItemStack(heldItemId);
		if (!stack.isEmpty()) {
			return stack.getHoverName().getString();
		}
		String z = com.mystaria.phantasmon.client.pokemon.VirtualItems.name(heldItemId);
		return z != null ? z : heldItemId;
	}

	public static void renderItemIcon(GuiGraphics graphics, ItemStack stack, float x, float y, float size) {
		PoseStack poseStack = graphics.pose();
		poseStack.pushPose();
		poseStack.translate(x, y, 0);
		float scale = size / 16f;
		poseStack.scale(scale, scale, 1f);
		graphics.renderItem(stack, 0, 0);
		poseStack.popPose();
	}

	/** Black or white text, whichever reads better on {@code argbColor} (luminance threshold 0.6, same rule as the PC and the trade spec §4). */
	public static int readableTextColor(int argbColor) {
		int r = (argbColor >> 16) & 0xFF;
		int g = (argbColor >> 8) & 0xFF;
		int b = argbColor & 0xFF;
		double luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
		return luminance > 0.6 ? 0x000000 : 0xFFFFFF;
	}
}
