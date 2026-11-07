package com.mystaria.phantasmon.client.pokemon;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import com.mystaria.phantasmon.client.pokemon.showdown.CobblemonIdentifiers;

/**
 * Every Cobblemon item that actually does something in a Pokémon battle (Leftovers, Choice
 * Band, Life Orb, berries, ...), keyed by its item registry path (e.g. {@code "choice_band""} —
 * matching the format {@code CobblemonIdentifiers.slugUnderscore} already produces for Showdown
 * imports, see {@code ShowdownImportMapper} — not every item Cobblemon lets a Pokémon
 * cosmetically hold.
 *
 * <p>Sourced from Cobblemon's own {@code #cobblemon:held/is_held_item} item tag — the exact list
 * Cobblemon itself uses to know an item is a real battle-effect held item, as opposed to a
 * utility item/block that merely happens to be a {@code cobblemon:}-namespaced item (Adrien:
 * 2026-09-29 — filtering by namespace alone let non-battle items through). An earlier attempt
 * filtered on the {@code HeldItemEffectComponent} data component instead, assuming it was set as
 * a default {@link Item} component; that returned zero items (Adrien confirmed: no item was found
 * at all, in either language), meaning it's not populated that way — the item tag is the actual
 * data-driven source of truth here and is much simpler besides.
 */
public final class CobblemonHeldItems {

	private static final TagKey<Item> IS_HELD_ITEM = TagKey.create(Registries.ITEM,
			ResourceLocation.fromNamespaceAndPath("cobblemon", "held/is_held_item"));

	/** Showdown ids whose Cobblemon item goes by another name. */
	private static final Map<String, ResourceLocation> ALIASES = Map.of(
			"charcoal", ResourceLocation.fromNamespaceAndPath("cobblemon", "charcoal_stick"),
			"leek", ResourceLocation.fromNamespaceAndPath("cobblemon", "medicinal_leek"),
			"thickclub", ResourceLocation.withDefaultNamespace("bone"));

	private static Map<String, Item> byId;
	private static Map<String, Item> byShowdownId;

	private CobblemonHeldItems() {
	}

	/**
	 * The items the editor offers: Cobblemon's held items. Scanned once per game
	 * session (tags are loaded well before this screen can open) and cached.
	 */
	public static Map<String, Item> byId() {
		if (byId == null) {
			Map<String, Item> map = new LinkedHashMap<>();
			for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(IS_HELD_ITEM)) {
				Item item = holder.value();
				ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
				map.put(id.getPath(), item);
			}
			byId = map;
		}
		return byId;
	}

	/**
	 * A stored held item id to its item, or null. Exact registry path first; otherwise compared the way Showdown
	 * compares ids — lower case, letters and digits only — against the offered items, then any {@code cobblemon:}
	 * item: a Showdown import stores "Charizardite X" as {@code charizardite_x}, the item is
	 * {@code cobblemon:charizarditex}.
	 */
	public static Item resolve(String heldItemId) {
		// Ours even when another mod of the pack registers an item of that name: the same behaviour everywhere.
		if (heldItemId == null || heldItemId.isBlank() || VirtualItems.isVirtual(heldItemId)) {
			return null;
		}
		String id = heldItemId.trim().toLowerCase(Locale.ROOT);
		int colon = id.indexOf(':');
		if (colon >= 0) {
			id = id.substring(colon + 1);
		}
		Item exact = byId().get(id);
		if (exact != null) {
			return exact;
		}
		if (byShowdownId == null) {
			Map<String, Item> map = new HashMap<>();
			byId().forEach((path, item) -> map.putIfAbsent(CobblemonIdentifiers.slugConcat(path), item));
			for (Item item : BuiltInRegistries.ITEM) {
				ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
				if ("cobblemon".equals(key.getNamespace())) {
					map.putIfAbsent(CobblemonIdentifiers.slugConcat(key.getPath()), item);
				}
			}
			byShowdownId = map;
		}
		String showdown = CobblemonIdentifiers.slugConcat(id);
		ResourceLocation alias = ALIASES.get(showdown);
		if (alias != null) {
			return BuiltInRegistries.ITEM.get(alias);
		}
		return byShowdownId.get(showdown);
	}
}
