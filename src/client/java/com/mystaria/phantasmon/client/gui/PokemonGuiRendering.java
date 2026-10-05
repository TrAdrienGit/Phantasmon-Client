package com.mystaria.phantasmon.client.gui;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.client.gui.PokemonGuiUtilsKt;
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
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
		RenderablePokemon renderable = new RenderablePokemon(species, aspects, ItemStack.EMPTY);

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
					new FloatingState(),
					0f,
					ICON_INNER_SCALE,
					null,
					false,
					0f, 0f, 0f, 0f, 0f, 0f,
					0,
					65416,
					null);
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
