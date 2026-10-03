package com.mystaria.phantasmon.client.battle;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cobblemon.mod.common.api.moves.animations.ActionEffectTimeline;
import com.cobblemon.mod.common.api.moves.animations.ActionEffects;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.resources.ResourceLocation;

/**
 * Cobblemon's action effects ({@code data/<namespace>/action_effects/**.json}: move animations, boosts,
 * statuses...) are server datapack data, never synced to clients. With an integrated server (singleplayer,
 * LAN host) {@link ActionEffects} is already filled; on a client connected to a LAN or remote server it is
 * empty, which both the host's engine ({@code BoostInstruction} even dereferences {@code boost} with
 * {@code !!}) and {@link ActionEffectPlayer} need. This fills it from the mods' own jars — Cobblemon's and
 * any addon shipping action effects — with Cobblemon's own parser, the same ids the datapack loader gives
 * (namespace + file name, folders dropped).
 */
public final class ClientActionEffects {

	private static final Logger LOG = LoggerFactory.getLogger(ClientActionEffects.class);

	private static boolean loadedByUs;
	private static Map<ActionEffectTimeline, ResourceLocation> idsByTimeline = new IdentityHashMap<>();
	private static int indexedSize = -1;

	private ClientActionEffects() {
	}

	public static synchronized void ensureLoaded() {
		if (loadedByUs || !ActionEffects.INSTANCE.getActionEffects().isEmpty()) {
			return;
		}
		long start = System.currentTimeMillis();
		Map<ResourceLocation, ActionEffectTimeline> effects = new HashMap<>();
		for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
			for (Path root : mod.getRootPaths()) {
				loadFrom(root.resolve("data"), effects);
			}
		}
		ActionEffects.INSTANCE.reload(effects);
		loadedByUs = true;
		LOG.info("Loaded {} Cobblemon action effects from mod jars in {} ms", effects.size(), System.currentTimeMillis() - start);
	}

	/** Reverse lookup: the registry id of a timeline instance (what the guest gets told to play). */
	public static synchronized ResourceLocation idOf(ActionEffectTimeline timeline) {
		Map<ResourceLocation, ActionEffectTimeline> all = ActionEffects.INSTANCE.getActionEffects();
		if (all.size() != indexedSize) {
			idsByTimeline = new IdentityHashMap<>();
			all.forEach((id, effect) -> idsByTimeline.put(effect, id));
			indexedSize = all.size();
		}
		return idsByTimeline.get(timeline);
	}

	private static void loadFrom(Path data, Map<ResourceLocation, ActionEffectTimeline> effects) {
		if (!Files.isDirectory(data)) {
			return;
		}
		try (Stream<Path> namespaces = Files.list(data)) {
			namespaces.filter(Files::isDirectory).forEach(namespaceDir -> {
				String namespace = namespaceDir.getFileName().toString().replace("/", "");
				Path folder = namespaceDir.resolve("action_effects");
				if (!Files.isDirectory(folder)) {
					return;
				}
				try (Stream<Path> files = Files.walk(folder)) {
					files.filter(path -> path.toString().endsWith(".json")).forEach(path -> {
						String name = path.getFileName().toString();
						ResourceLocation id = ResourceLocation.tryBuild(namespace, name.substring(0, name.length() - 5));
						if (id == null) {
							return;
						}
						try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
							effects.put(id, ActionEffects.INSTANCE.parse(reader, id));
						} catch (Exception ex) {
							LOG.warn("Cannot parse action effect {}", id, ex);
						}
					});
				} catch (IOException ex) {
					LOG.warn("Cannot list action effects in {}", folder, ex);
				}
			});
		} catch (IOException ex) {
			LOG.warn("Cannot list data namespaces in {}", data, ex);
		}
	}
}
