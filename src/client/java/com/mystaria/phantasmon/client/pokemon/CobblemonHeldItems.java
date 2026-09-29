package com.mystaria.phantasmon.client.pokemon;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

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

	private static Map<String, Item> byId;

	private CobblemonHeldItems() {
	}

	/** Scanned once per game session (tags are loaded well before this screen can open) and cached. */
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
}
