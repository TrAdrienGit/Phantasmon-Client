package com.mystaria.phantasmon.client.ghost;

import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.keybind.keybinds.HidePartyBinding;
import com.cobblemon.mod.common.pokemon.Species;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import com.mystaria.phantasmon.client.auth.AuthSession;
import com.mystaria.phantasmon.client.gui.PhantasmonCanvasScreen;
import com.mystaria.phantasmon.client.gui.PokemonGuiRendering;
import com.mystaria.phantasmon.client.pokemon.PokemonClient;
import com.mystaria.phantasmon.client.pokemon.PokemonDto;
import com.mystaria.phantasmon.client.pokemon.PokemonGender;

/**
 * Ghost team overlay on the left of the screen (Adrien 2026-10-04/05), the Phantasmon counterpart of Cobblemon's
 * party overlay: same layout, sizes and positions as Cobblemon's ({@code PartyOverlay}: 62×30 slots every 34 px,
 * six positions, empty ones collapsed, level / held item / portrait / bar / name / gender / ball), in the
 * Phantasmon colours. The slot frames are Cobblemon's own textures, read from its jar at runtime and recoloured
 * (their grey levels mapped to the Phantasmon palette) — nothing of Cobblemon's is shipped with this mod.
 *
 * <p>Cobblemon's "hide party" key (O by default) cycles <b>Cobblemon team → Ghost team → nothing → …</b>
 * ({@code HidePartyBindingMixin}). The team is read from the backend when the overlay opens, then every
 * {@link #REFRESH_MS} while it shows; the Ghost currently sent out uses the highlighted slot.
 */
public final class GhostPartyHud {

	public enum Mode { COBBLEMON, GHOST, NONE }

	private static final Logger LOG = LoggerFactory.getLogger(GhostPartyHud.class);
	private static final long REFRESH_MS = 10_000;

	// ---- Cobblemon's PartyOverlay geometry (GUI px) ----
	private static final int SLOT_W = 62;
	private static final int SLOT_H = 30;
	private static final int SLOT_PITCH = 34;
	private static final int SLOTS = 6;

	private static final int BAR_FULL = 0xFF50E6FF;
	private static final int BAR_DIM = 0xFF2FB7C9;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int LEVEL_TEXT = 0xFFCBEAF0;
	private static final int STAR = 0xFFFFDD33;

	/** Cobblemon grey level → Phantasmon colour (ARGB), per slot texture. */
	private static final Map<Integer, Integer> NORMAL_PALETTE = Map.of(
			47, 0xFF060E1A, 75, 0xFF0C2436, 89, 0xFF133650, 103, 0xFF10304A,
			122, 0xFF1E5E78, 141, 0xFF2A86A3, 198, 0xFF50E6FF);
	private static final Map<Integer, Integer> ACTIVE_PALETTE = Map.of(
			47, 0xFF060E1A, 103, 0xFF2A86A3, 122, 0xFF3FA9C6, 141, 0xFF17506C,
			169, 0xFF6FEFFF, 198, 0xFF7CEBFF, 255, 0xFFFFFFFF);
	private static final Map<Integer, Integer> COLLAPSED_PALETTE = Map.of(
			47, 0xFF060E1A, 75, 0xFF0C2436, 103, 0xFF10304A, 141, 0xFF2A86A3);

	private static final ResourceLocation PORTRAIT_BACKGROUND = cobblemon("textures/gui/party/party_slot_portrait_background.png");
	private static final ResourceLocation GENDER_MALE = cobblemon("textures/gui/party/party_gender_male.png");
	private static final ResourceLocation GENDER_FEMALE = cobblemon("textures/gui/party/party_gender_female.png");
	private static final ResourceLocation BALL = cobblemon("textures/gui/ball/poke_ball.png");

	private static GhostPartyHud instance;

	private final PokemonClient pokemonClient;
	private final AuthSession session;
	private final GhostSession ghostSession;

	private Mode mode;
	/** Index = team slot - 1; null = empty slot. */
	private volatile PokemonDto[] team = new PokemonDto[SLOTS];
	private volatile boolean loaded;
	private long lastRefresh;
	private boolean refreshing;
	/** Selected slot (TODO-23), moved by Cobblemon's up/down party keys; R sends it out. */
	private int selected;

	private final Map<String, ResourceLocation> recoloured = new HashMap<>();

	public GhostPartyHud(PokemonClient pokemonClient, AuthSession session, GhostSession ghostSession) {
		this.pokemonClient = pokemonClient;
		this.session = session;
		this.ghostSession = ghostSession;
		instance = this;
	}

	public static GhostPartyHud instance() {
		return instance;
	}

	// ---- Key cycle ----

	/** Cobblemon's hide-party key: Cobblemon → Ghost → none → Cobblemon. */
	public void cycle() {
		if (mode == null) {
			mode = HidePartyBinding.INSTANCE.getShouldHide() ? Mode.NONE : Mode.COBBLEMON;
		}
		mode = switch (mode) {
			case COBBLEMON -> Mode.GHOST;
			case GHOST -> Mode.NONE;
			case NONE -> Mode.COBBLEMON;
		};
		HidePartyBinding.INSTANCE.setShouldHide(mode != Mode.COBBLEMON);
		if (mode == Mode.GHOST) {
			refresh();
		}
	}

	/**
	 * The team may have changed (PC closed, trade done): re-read it on the next tick if the overlay shows, instead of
	 * waiting for the periodic refresh (Adrien 2026-10-05: a Pokémon put in the team didn't appear).
	 */
	public static void teamChanged() {
		if (instance != null) {
			instance.lastRefresh = 0;
		}
	}

	/** Called every client tick: keeps the team fresh while it shows. */
	public void tick() {
		if (mode == Mode.GHOST && System.currentTimeMillis() - lastRefresh >= REFRESH_MS) {
			refresh();
		}
	}

	private void refresh() {
		lastRefresh = System.currentTimeMillis();
		if (refreshing || !session.isAuthenticated()) {
			return;
		}
		refreshing = true;
		pokemonClient.listForOwner(session.accessToken(), session.playerUuid())
				.thenAccept(pokemons -> {
					PokemonDto[] slots = new PokemonDto[SLOTS];
					Arrays.stream(pokemons)
							.filter(pokemon -> pokemon.teamSlot() != null && pokemon.teamSlot() >= 1 && pokemon.teamSlot() <= SLOTS)
							.forEach(pokemon -> slots[pokemon.teamSlot() - 1] = pokemon);
					Minecraft.getInstance().execute(() -> {
						team = slots;
						loaded = true;
						refreshing = false;
						if (slots[selected] == null) {
							shift(1);
						}
					});
				})
				.exceptionally(ex -> {
					Minecraft.getInstance().execute(() -> refreshing = false);
					return null;
				});
	}

	// ---- Selection and send-out (Cobblemon's up / down / R keys, TODO-23) ----

	/** Whether Cobblemon's party keys act on the Ghost team right now (overlay shown, no screen, no battle). */
	public boolean ownsPartyKeys() {
		Minecraft mc = Minecraft.getInstance();
		return mode == Mode.GHOST && mc.screen == null && mc.player != null && CobblemonClient.INSTANCE.getBattle() == null;
	}

	/**
	 * Whether R is ours: party keys are, and the player is neither riding nor aiming at a real entity (a player,
	 * a real Pokémon...) — those stay Cobblemon's (interaction wheel, challenge, dismount). Aiming at a Ghost is fine.
	 */
	public boolean ownsSendKey() {
		Minecraft mc = Minecraft.getInstance();
		if (!ownsPartyKeys() || mc.player.isSpectator() || mc.player.getVehicle() != null) {
			return false;
		}
		var player = mc.player;
		var eye = player.getEyePosition();
		var reach = eye.add(player.getViewVector(1f).scale(10.0));
		var box = player.getBoundingBox().expandTowards(player.getViewVector(1f).scale(10.0)).inflate(1.0);
		var hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(player, eye, reach, box,
				entity -> !entity.isSpectator() && entity.isPickable()
						&& !(entity instanceof com.cobblemon.mod.common.entity.pokemon.PokemonEntity pokemon && PhantasmonEntities.isPhantasmon(pokemon)),
				100.0);
		return hit == null;
	}

	/** Up (-1) / down (+1): next Ghost in the team, wrapping around, empty slots skipped. */
	public void shift(int delta) {
		PokemonDto[] slots = team;
		for (int step = 1; step <= SLOTS; step++) {
			int index = Math.floorMod(selected + delta * step, SLOTS);
			if (slots[index] != null) {
				selected = index;
				return;
			}
		}
	}

	/** R: sends the selected Ghost out — recalling whichever is out first — or recalls it if it already is. */
	public void sendSelected() {
		PokemonDto pokemon = team[selected];
		if (pokemon == null) {
			shift(1);
			pokemon = team[selected];
		}
		if (pokemon == null || pokemon.uuid() == null) {
			return;
		}
		UUID active = ghostSession.activeGhostPokemonUuid();
		if (pokemon.uuid().equals(active)) {
			ghostSession.recall();
			return;
		}
		Component unrecognized = com.mystaria.phantasmon.client.pokemon.PokemonRecognition.problem(pokemon);
		if (unrecognized != null) {
			Minecraft.getInstance().player.displayClientMessage(unrecognized, false);
			return;
		}
		if (active != null) {
			ghostSession.recall();
		}
		ghostSession.sendOut(pokemon.uuid());
	}

	// ---- Rendering ----

	public void render(GuiGraphics g) {
		Minecraft mc = Minecraft.getInstance();
		if (mode != Mode.GHOST || mc.options.hideGui || mc.player == null || CobblemonClient.INSTANCE.getBattle() != null) {
			return;
		}
		Font font = mc.font;
		// Same vertical placement as Cobblemon's overlay, so switching between the two doesn't move anything.
		int top = g.guiHeight() / 2 - SLOTS * SLOT_H / 2 - 10;

		// Small Phantasmon tag above the first slot: tells the two overlays apart.
		String tag = Component.translatable("phantasmon.ghost.hud.title").getString().toUpperCase(java.util.Locale.ROOT);
		drawText(g, font, tag, 2.5f, top - 5f, 0.5f, BAR_FULL, true);

		PokemonDto[] slots = team;
		boolean empty = Arrays.stream(slots).allMatch(java.util.Objects::isNull);
		if (!session.isAuthenticated() || !loaded || empty) {
			String key = !session.isAuthenticated() ? "phantasmon.ghost.hud.offline"
					: !loaded ? "phantasmon.ghost.hud.loading" : "phantasmon.ghost.hud.empty";
			drawText(g, font, Component.translatable(key).getString(), 9f, top + 1f, 0.5f, LEVEL_TEXT, true);
		}
		UUID active = ghostSession.activeGhostPokemonUuid();
		for (int i = 0; i < SLOTS; i++) {
			int y = top + i * SLOT_PITCH;
			PokemonDto pokemon = slots[i];
			if (pokemon == null) {
				blitSlot(g, "party_slot_collapsed", COLLAPSED_PALETTE, 0, y);
			} else {
				renderSlot(g, font, pokemon, 0, y, i == selected, pokemon.uuid() != null && pokemon.uuid().equals(active));
			}
		}
	}

	/**
	 * One slot, in Cobblemon's order: portrait background, model, frame, then texts and icons on top. Like
	 * Cobblemon: the selected slot sticks out, the Pokémon out in the world has its ball open.
	 */
	private void renderSlot(GuiGraphics g, Font font, PokemonDto pokemon, int slotX, int y, boolean active, boolean out) {
		// Cobblemon's highlighted slot texture has its whole content 6 px further right (the slot "sticks out").
		int x = slotX + (active ? 6 : 0);
		g.blit(PORTRAIT_BACKGROUND, x + 22, y + 2, 0, 0, 21, 21, 21, 21);
		g.enableScissor(x + 23, y + 4, x + 42, y + 21);
		float modelScale = 1.9f;
		PokemonGuiRendering.renderModel(g, pokemon.species(), pokemon.form(), pokemon.isShiny(), PokemonGuiRendering.storedGender(pokemon),
				x + 32.5f, y + 12.5f - 7.9f * modelScale + 20f * modelScale / 8.25f, modelScale);
		g.disableScissor();
		g.flush();

		blitSlot(g, active ? "party_slot_active" : "party_slot", active ? ACTIVE_PALETTE : NORMAL_PALETTE, slotX, y);

		// Level, centred on the left column, two lines like Cobblemon's ("Nv." / number).
		drawCentered(g, font, Component.translatable("phantasmon.ghost.hud.level").getString(), x + 6.5f, y + 13f, 0.5f, LEVEL_TEXT);
		drawCentered(g, font, String.valueOf(pokemon.level()), x + 6.5f, y + 18f, 0.5f, TEXT);

		// Held item in its little box.
		Object heldItem = pokemon.data() == null ? null : pokemon.data().get("held_item");
		ItemStack item = heldItem == null ? ItemStack.EMPTY : PokemonGuiRendering.heldItemStack(heldItem.toString());
		if (!item.isEmpty()) {
			PokemonGuiRendering.renderItemIcon(g, item, x + 12, y + 14, 8);
		}

		// Ghosts have no HP outside a battle: the bar is always full, in Phantasmon cyan.
		g.fillGradient(x + 46, y + 4, x + 48, y + 22, BAR_FULL, BAR_DIM);

		// Name (★ if shiny) and gender, in the dark band.
		float nameX = x + 2.5f;
		if (pokemon.isShiny()) {
			drawText(g, font, "★", nameX, y + 25f, 0.5f, STAR, true);
			nameX += 4.5f;
		}
		String name = fit(font, PhantasmonCanvasScreen.displayName(pokemon), (int) ((x + 39 - nameX) / 0.5f));
		drawText(g, font, name, nameX, y + 25f, 0.5f, TEXT, true);
		PokemonGender gender = gender(pokemon);
		if (gender == PokemonGender.MALE || gender == PokemonGender.FEMALE) {
			blitScaled(g, gender == PokemonGender.MALE ? GENDER_MALE : GENDER_FEMALE, x + 40f, y + 25f, 0, 0, 5, 7, 5, 7, 0.5f);
		}

		// Ball at the tip of the slot.
		blitScaled(g, BALL, x + 43.5f, y + 22f, 0, out ? 22 : 0, 18, 22, 18, 44, 0.5f);
	}

	private void blitSlot(GuiGraphics g, String name, Map<Integer, Integer> palette, int x, int y) {
		ResourceLocation texture = recoloured(name, palette);
		if (texture != null) {
			g.blit(texture, x, y, 0, 0, SLOT_W, SLOT_H, SLOT_W, SLOT_H);
		}
	}

	/**
	 * Cobblemon's slot texture with its grey levels swapped for the Phantasmon palette, built once from the
	 * texture Cobblemon ships (so it follows Cobblemon's resource packs too). Null if it can't be read.
	 */
	private ResourceLocation recoloured(String name, Map<Integer, Integer> palette) {
		if (recoloured.containsKey(name)) {
			return recoloured.get(name);
		}
		ResourceLocation result = null;
		Minecraft mc = Minecraft.getInstance();
		try {
			var resource = mc.getResourceManager().getResource(cobblemon("textures/gui/party/" + name + ".png"));
			if (resource.isPresent()) {
				try (InputStream stream = resource.get().open()) {
					NativeImage image = NativeImage.read(stream);
					for (int px = 0; px < image.getWidth(); px++) {
						for (int py = 0; py < image.getHeight(); py++) {
							int abgr = image.getPixelRGBA(px, py);
							int alpha = abgr >>> 24;
							if (alpha == 0) {
								continue;
							}
							int grey = abgr & 0xFF;
							Integer argb = palette.get(grey);
							if (argb == null) {
								argb = nearest(palette, grey);
							}
							int r = (argb >> 16) & 0xFF;
							int gr = (argb >> 8) & 0xFF;
							int b = argb & 0xFF;
							image.setPixelRGBA(px, py, (alpha << 24) | (b << 16) | (gr << 8) | r);
						}
					}
					result = ResourceLocation.fromNamespaceAndPath("phantasmon", "dynamic/ghost_" + name);
					mc.getTextureManager().register(result, new DynamicTexture(image));
				}
			}
		} catch (Exception ex) {
			LOG.warn("Cannot build the Ghost party slot texture from Cobblemon's {}", name, ex);
		}
		recoloured.put(name, result);
		return result;
	}

	private static int nearest(Map<Integer, Integer> palette, int grey) {
		int best = -1;
		int bestDistance = Integer.MAX_VALUE;
		for (int key : palette.keySet()) {
			int distance = Math.abs(key - grey);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = key;
			}
		}
		return palette.get(best);
	}

	private static void blitScaled(GuiGraphics g, ResourceLocation texture, float x, float y, int u, int v, int width, int height,
			int textureWidth, int textureHeight, float scale) {
		PoseStack pose = g.pose();
		pose.pushPose();
		pose.translate(x, y, 0);
		pose.scale(scale, scale, 1f);
		g.blit(texture, 0, 0, u, v, width, height, textureWidth, textureHeight);
		pose.popPose();
	}

	private static PokemonGender gender(PokemonDto pokemon) {
		Species species = PokemonSpecies.INSTANCE.getByName(pokemon.species());
		Float ratio = species == null ? null : species.getMaleRatio();
		return PokemonGender.resolve(PokemonGuiRendering.storedGender(pokemon), ratio);
	}

	private static void drawText(GuiGraphics g, Font font, String text, float x, float y, float scale, int color, boolean shadow) {
		PoseStack pose = g.pose();
		pose.pushPose();
		pose.translate(x, y, 0);
		pose.scale(scale, scale, 1f);
		g.drawString(font, text, 0, 0, color, shadow);
		pose.popPose();
	}

	private static void drawCentered(GuiGraphics g, Font font, String text, float centerX, float y, float scale, int color) {
		drawText(g, font, text, centerX - font.width(text) * scale / 2f, y, scale, color, true);
	}

	private static String fit(Font font, String text, int maxWidth) {
		if (font.width(text) <= maxWidth) {
			return text;
		}
		return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("…"))) + "…";
	}

	private static ResourceLocation cobblemon(String path) {
		return ResourceLocation.fromNamespaceAndPath("cobblemon", path);
	}
}
