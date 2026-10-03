package com.mystaria.phantasmon.client.battle;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.scheduling.ServerRealTimeTaskTracker;
import com.cobblemon.mod.common.api.scheduling.ServerTaskTracker;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.battles.runner.ShowdownService;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;

/**
 * The one thread every Ghost battle runs on (Phase 9). Cobblemon's battle
 * engine is server code: a single-threaded GraalJS Showdown context plus
 * {@link BattleRegistry} ticked once per server tick. On the host client:
 * <ul>
 *   <li><b>integrated server present</b> (singleplayer / "open to LAN" host):
 *   everything is submitted to that server thread — it already owns a booted,
 *   fully fed Showdown service and already ticks {@code BattleRegistry}, and a
 *   second thread touching the same GraalJS context would crash it;</li>
 *   <li><b>pure client</b> (connected to a dedicated server): a private
 *   daemon thread boots Cobblemon's Showdown service once, feeds it the data
 *   a server would (species from the synced registry, the few JS item
 *   scripts shipped in Cobblemon's jar) and ticks {@code BattleRegistry} every
 *   50 ms.</li>
 * </ul>
 */
public final class BattleThread {

	private static final Logger LOG = LoggerFactory.getLogger(BattleThread.class);
	private static final BattleThread INSTANCE = new BattleThread();
	private static final String OWN_THREAD_NAME = "phantasmon-battle";

	private ScheduledExecutorService own;
	private volatile boolean showdownBooted;

	private BattleThread() {
	}

	public static BattleThread get() {
		return INSTANCE;
	}

	public void submit(Runnable task) {
		Runnable safe = () -> {
			try {
				task.run();
			} catch (Exception ex) {
				LOG.error("Ghost battle task failed", ex);
			}
		};
		MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
		if (server != null) {
			server.execute(safe);
		} else {
			ownThread().execute(safe);
		}
	}

	/** Whether the caller is the private battle thread (pure client only — see {@code DistributionUtilsMixin}). */
	public static boolean isOwnThread() {
		return OWN_THREAD_NAME.equals(Thread.currentThread().getName());
	}

	/** Queues work on the private battle thread, the pure-client stand-in for {@code MinecraftServer.execute}. */
	public void executeOnOwnThread(Runnable task) {
		ownThread().execute(task);
	}

	/** Must run on the battle thread (inside {@link #submit}). No-op when an integrated server already booted Showdown. */
	public void ensureShowdown() {
		if (Minecraft.getInstance().getSingleplayerServer() != null || showdownBooted) {
			return;
		}
		long start = System.currentTimeMillis();
		ShowdownService service = ShowdownService.Companion.getService();
		service.openConnection();
		service.sendRegistryData(jarScripts("data/cobblemon/bag_items"), "bagItem");
		service.sendRegistryData(jarScripts("data/cobblemon/held_items"), "heldItem");
		service.sendRegistryData(showdownSpecies(), "species");
		service.indicateSpeciesInitialized();
		// Move animations, boosts, statuses: server datapack data the engine needs too (BoostInstruction).
		ClientActionEffects.ensureLoaded();
		showdownBooted = true;
		LOG.info("Showdown booted on the client for Ghost battles in {} ms", System.currentTimeMillis() - start);
	}

	private synchronized ScheduledExecutorService ownThread() {
		if (own == null) {
			own = Executors.newSingleThreadScheduledExecutor(runnable -> {
				Thread thread = new Thread(runnable, OWN_THREAD_NAME);
				thread.setDaemon(true);
				return thread;
			});
			own.scheduleAtFixedRate(() -> {
				if (Minecraft.getInstance().getSingleplayerServer() == null) {
					try {
						BattleRegistry.INSTANCE.tick();
						// The battle engine schedules animation delays (send-outs, faints...) on Cobblemon's
						// server-side task trackers, normally advanced by the server tick — do it here too.
						ServerTaskTracker.INSTANCE.update(1 / 20F);
						ServerRealTimeTaskTracker.INSTANCE.update();
					} catch (Exception ex) {
						LOG.error("BattleRegistry tick failed", ex);
					}
				}
			}, 50, 50, TimeUnit.MILLISECONDS);
		}
		return own;
	}

	/** {@code PokemonSpecies.allShowdownSpecies()} is Kotlin-{@code internal} (JVM name mangled), hence reflection. */
	@SuppressWarnings("unchecked")
	private static Map<String, String> showdownSpecies() {
		for (Method method : PokemonSpecies.class.getDeclaredMethods()) {
			if (method.getName().startsWith("allShowdownSpecies") && method.getParameterCount() == 0) {
				try {
					method.setAccessible(true);
					return (Map<String, String>) method.invoke(PokemonSpecies.INSTANCE);
				} catch (ReflectiveOperationException ex) {
					throw new IllegalStateException("Cannot export Cobblemon species to Showdown", ex);
				}
			}
		}
		throw new IllegalStateException("PokemonSpecies.allShowdownSpecies not found (Cobblemon API changed?)");
	}

	/** The JS scripts a server would load from Cobblemon's datapack folder, read straight from Cobblemon's jar. */
	private static Map<String, String> jarScripts(String folder) {
		Map<String, String> scripts = new HashMap<>();
		FabricLoader.getInstance().getModContainer("cobblemon").flatMap(mod -> mod.findPath(folder)).ifPresent(root -> {
			try (Stream<Path> files = Files.walk(root)) {
				files.filter(path -> path.toString().endsWith(".js")).forEach(path -> {
					try {
						String name = path.getFileName().toString();
						scripts.put(name.substring(0, name.length() - 3), Files.readString(path, StandardCharsets.UTF_8));
					} catch (IOException ex) {
						LOG.warn("Cannot read Showdown script {}", path, ex);
					}
				});
			} catch (IOException ex) {
				LOG.warn("Cannot list Showdown scripts in {}", folder, ex);
			}
		});
		return scripts;
	}
}
