package com.mystaria.phantasmon.client.battle;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cobblemon.mod.common.battles.runner.ShowdownService;

/**
 * Code an add-on installs in the battle engine (2026-11-18; Phantasmon Events' League Reborn: rules and conditions
 * added to Showdown's data). Each extension is a JavaScript program evaluated in Cobblemon's Showdown context, once
 * per engine boot: {@link #apply} runs before every hosted battle, on the battle thread, and re-installs an extension
 * whose {@code installed} check says the engine lost it (a fresh boot, the integrated server's engine).
 * <p>
 * Only the host needs an extension: the guest's client just shows the packets it relays. Graal's polyglot API is not
 * on our compile classpath, so the context is reached by reflection ({@code GraalShowdownService.getContext()}).
 */
public final class ShowdownExtensions {

	private static final Logger LOG = LoggerFactory.getLogger(ShowdownExtensions.class);

	/**
	 * @param installed a JavaScript expression, true when the extension is in the engine
	 * @param script    the program installing it (read when needed)
	 */
	public record Extension(String installed, Supplier<String> script) {
	}

	private static final Map<String, Extension> EXTENSIONS = new ConcurrentHashMap<>();

	private ShowdownExtensions() {
	}

	/** Called by an add-on at startup. */
	public static void register(String id, String installed, Supplier<String> script) {
		EXTENSIONS.put(id, new Extension(installed, script));
	}

	/** Whether an add-on registered {@code id} (e.g. the lobby offers League Reborn's fields only then). */
	public static boolean has(String id) {
		return EXTENSIONS.containsKey(id);
	}

	/** Installs the missing extensions. Must run on the battle thread, after the engine booted. */
	static void apply() {
		if (EXTENSIONS.isEmpty()) {
			return;
		}
		Object context;
		Method eval;
		try {
			Object service = ShowdownService.Companion.getService();
			context = service.getClass().getMethod("getContext").invoke(service);
			eval = context.getClass().getMethod("eval", String.class, CharSequence.class);
		} catch (ReflectiveOperationException | RuntimeException ex) {
			LOG.warn("Battle engine extensions not installed: no Graal Showdown context ({})", ex.toString());
			return;
		}
		EXTENSIONS.forEach((id, extension) -> {
			try {
				Object present = eval.invoke(context, "js", "!!(" + extension.installed() + ")");
				if (present.getClass().getMethod("asBoolean").invoke(present) instanceof Boolean yes && yes) {
					return;
				}
				long start = System.currentTimeMillis();
				eval.invoke(context, "js", extension.script().get());
				LOG.info("Battle engine extension {} installed in {} ms", id, System.currentTimeMillis() - start);
			} catch (ReflectiveOperationException | RuntimeException ex) {
				Throwable cause = ex instanceof java.lang.reflect.InvocationTargetException ite && ite.getCause() != null
						? ite.getCause() : ex;
				LOG.error("Battle engine extension {} failed to install", id, cause);
			}
		});
	}
}
