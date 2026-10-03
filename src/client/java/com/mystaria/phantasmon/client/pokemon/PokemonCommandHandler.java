package com.mystaria.phantasmon.client.pokemon;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import com.mystaria.phantasmon.client.auth.AuthSession;
import com.mystaria.phantasmon.client.gui.PhantasmonPcScreen;
import com.mystaria.phantasmon.client.network.BackendApiException;
import com.mystaria.phantasmon.client.network.BackendErrorMessages;
import com.mystaria.phantasmon.client.pokemon.showdown.ShowdownImportMapper;
import com.mystaria.phantasmon.client.pokemon.showdown.ShowdownParseException;
import com.mystaria.phantasmon.client.pokemon.showdown.ShowdownParser;
import com.mystaria.phantasmon.client.pokemon.showdown.ShowdownPokemon;

/**
 * Business logic behind {@code /phantasmon pokemon *} (CAD Phase 6) — kept
 * separate from {@link com.mystaria.phantasmon.client.command.PhantasmonCommands}'s
 * brigadier wiring, same split as {@link com.mystaria.phantasmon.client.auth.AuthService}.
 * All-commands approach (Adrien: 2026-09-26) rather than a graphical PC/editor
 * screen — creation goes exclusively through Showdown import for now, per the
 * CAD's explicit "V1 minimum: import by command" allowance (Partie 1 §10).
 */
public final class PokemonCommandHandler {

	/** Matches Cobblemon's targeted version (see client conventions) — stamped on every Pokémon this client creates. Public: also used by {@link PhantasmonPcScreen}'s own clipboard import. */
	public static final String COBBLEMON_DATA_VERSION = "1.8.1";

	private final PokemonClient pokemonClient;
	private final AuthSession session;

	public PokemonCommandHandler(PokemonClient pokemonClient, AuthSession session) {
		this.pokemonClient = pokemonClient;
		this.session = session;
	}

	public void importFromClipboard(FabricClientCommandSource source) {
		if (!requireAuthenticated(source)) {
			return;
		}
		String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
		if (clipboard == null || clipboard.isBlank()) {
			source.sendError(Component.translatable("phantasmon.pokemon.import.clipboard_empty"));
			return;
		}

		List<ShowdownPokemon> parsed;
		try {
			parsed = ShowdownParser.parseTeam(clipboard);
		} catch (ShowdownParseException ex) {
			source.sendError(Component.translatable("phantasmon.pokemon.import.parse_error", ex.getMessage()));
			return;
		}

		String bearerToken = session.accessToken();
		for (ShowdownPokemon set : parsed) {
			importOne(source, bearerToken, set);
		}
	}

	private void importOne(FabricClientCommandSource source, String bearerToken, ShowdownPokemon set) {
		PokemonCreateRequestDto request;
		try {
			request = ShowdownImportMapper.toCreateRequest(set, COBBLEMON_DATA_VERSION);
		} catch (ShowdownParseException ex) {
			source.sendError(Component.translatable("phantasmon.pokemon.import.parse_error_for",
					set.speciesToken(), ex.getMessage()));
			return;
		}

		pokemonClient.create(bearerToken, request)
				.thenAccept(created -> feedback(source, Component.translatable(
						"phantasmon.pokemon.import.created", created.species(), created.uuid().toString())))
				.exceptionally(ex -> {
					reportFailure(source, ex, set.speciesToken());
					return null;
				});
	}

	public void list(FabricClientCommandSource source) {
		if (!requireAuthenticated(source)) {
			return;
		}
		pokemonClient.listForOwner(session.accessToken(), session.playerUuid())
				.thenAccept(pokemons -> {
					if (pokemons.length == 0) {
						feedback(source, Component.translatable("phantasmon.pokemon.list.empty"));
						return;
					}
					for (PokemonDto pokemon : pokemons) {
						feedback(source, summaryLine(pokemon));
					}
				})
				.exceptionally(ex -> {
					reportFailure(source, ex, null);
					return null;
				});
	}

	public void pcBox(FabricClientCommandSource source, int box) {
		if (!requireAuthenticated(source)) {
			return;
		}
		pokemonClient.pcBox(session.accessToken(), session.playerUuid(), box)
				.thenAccept(pokemons -> {
					feedback(source, Component.translatable("phantasmon.pokemon.pc.header", box));
					if (pokemons.length == 0) {
						feedback(source, Component.translatable("phantasmon.pokemon.pc.empty"));
						return;
					}
					for (PokemonDto pokemon : pokemons) {
						feedback(source, summaryLine(pokemon));
					}
				})
				.exceptionally(ex -> {
					reportFailure(source, ex, null);
					return null;
				});
	}

	public void delete(FabricClientCommandSource source, UUID pokemonUuid) {
		if (!requireAuthenticated(source)) {
			return;
		}
		pokemonClient.delete(session.accessToken(), pokemonUuid)
				.thenAccept(ignored -> feedback(source, Component.translatable("phantasmon.pokemon.delete.done", pokemonUuid.toString())))
				.exceptionally(ex -> {
					reportFailure(source, ex, null);
					return null;
				});
	}

	public void clone(FabricClientCommandSource source, UUID pokemonUuid) {
		if (!requireAuthenticated(source)) {
			return;
		}
		pokemonClient.clone(session.accessToken(), pokemonUuid)
				.thenAccept(cloned -> feedback(source, Component.translatable(
						"phantasmon.pokemon.clone.done", cloned.species(), cloned.uuid().toString())))
				.exceptionally(ex -> {
					reportFailure(source, ex, null);
					return null;
				});
	}

	public void editLevel(FabricClientCommandSource source, UUID pokemonUuid, int level) {
		if (!requireAuthenticated(source)) {
			return;
		}
		pokemonClient.update(session.accessToken(), pokemonUuid, PokemonUpdateRequestDto.setLevel(level))
				.thenAccept(updated -> feedback(source, Component.translatable(
						"phantasmon.pokemon.edit.level_done", updated.uuid().toString(), updated.level())))
				.exceptionally(ex -> {
					reportFailure(source, ex, null);
					return null;
				});
	}

	public void teamSet(FabricClientCommandSource source, UUID pokemonUuid, int teamSlot) {
		if (!requireAuthenticated(source)) {
			return;
		}
		pokemonClient.update(session.accessToken(), pokemonUuid, PokemonUpdateRequestDto.movingToTeamSlot(teamSlot))
				.thenAccept(updated -> feedback(source, Component.translatable(
						"phantasmon.pokemon.team.set_done", updated.species(), teamSlot)))
				.exceptionally(ex -> {
					reportFailure(source, ex, null);
					return null;
				});
	}

	public void teamClear(FabricClientCommandSource source, UUID pokemonUuid, int boxId, int boxSlot) {
		if (!requireAuthenticated(source)) {
			return;
		}
		pokemonClient.update(session.accessToken(), pokemonUuid, PokemonUpdateRequestDto.movingToPcSlot(boxId, boxSlot))
				.thenAccept(updated -> feedback(source, Component.translatable(
						"phantasmon.pokemon.team.clear_done", updated.species(), boxId, boxSlot)))
				.exceptionally(ex -> {
					reportFailure(source, ex, null);
					return null;
				});
	}

	/** General PC-slot move (PC→PC repositioning, or team→PC — same underlying operation as {@link #teamClear}). */
	public void pcMove(FabricClientCommandSource source, UUID pokemonUuid, int boxId, int boxSlot) {
		if (!requireAuthenticated(source)) {
			return;
		}
		pokemonClient.update(session.accessToken(), pokemonUuid, PokemonUpdateRequestDto.movingToPcSlot(boxId, boxSlot))
				.thenAccept(updated -> feedback(source, Component.translatable(
						"phantasmon.pokemon.pc.move_done", updated.species(), boxId, boxSlot)))
				.exceptionally(ex -> {
					reportFailure(source, ex, null);
					return null;
				});
	}

	public void teamView(FabricClientCommandSource source) {
		if (!requireAuthenticated(source)) {
			return;
		}
		pokemonClient.listForOwner(session.accessToken(), session.playerUuid())
				.thenAccept(pokemons -> {
					List<PokemonDto> team = java.util.Arrays.stream(pokemons)
							.filter(pokemon -> pokemon.teamSlot() != null)
							.sorted(java.util.Comparator.comparingInt(PokemonDto::teamSlot))
							.toList();
					if (team.isEmpty()) {
						feedback(source, Component.translatable("phantasmon.pokemon.team.empty"));
						return;
					}
					for (PokemonDto pokemon : team) {
						feedback(source, Component.literal("[" + pokemon.teamSlot() + "] ").append(summaryLine(pokemon)));
					}
				})
				.exceptionally(ex -> {
					reportFailure(source, ex, null);
					return null;
				});
	}

	private volatile boolean pcScreenRequested;

	/**
	 * Opens the graphical PC screen (Adrien: 2026-09-27, HUD phase 1) — the
	 * command-based {@code pc <box>}/{@code pc move} above remain available
	 * alongside it. Deliberately does <b>not</b> call {@code setScreen}
	 * synchronously here: decompiling {@code ChatScreen.keyPressed} confirms it
	 * calls {@code handleChatInput(...)} (which dispatches this very command)
	 * and then <i>unconditionally</i> calls {@code minecraft.setScreen(null)}
	 * right after, with no check on what screen is current — opening our
	 * screen synchronously during command dispatch means the chat screen's own
	 * close would immediately wipe it out again (confirmed live: Adrien saw
	 * nothing happen even though the screen briefly existed). Instead this just
	 * raises a flag consumed by {@link #tick()} on the next client tick, by
	 * which point chat has already finished closing itself.
	 */
	public void openPc(FabricClientCommandSource source) {
		if (!requireAuthenticated(source)) {
			return;
		}
		pcScreenRequested = true;
	}

	/** Same as {@link #openPc(FabricClientCommandSource)}, for the {@code open_pc} keybind (Adrien: 2026-09-29) — a keybind has no command source to report a "not authenticated" error through, so this reports directly to chat instead. */
	public void openPc() {
		if (!session.isAuthenticated()) {
			chatMessage(Component.translatable("phantasmon.error.not_authenticated"));
			return;
		}
		pcScreenRequested = true;
	}

	/**
	 * Sends out whichever Pokémon currently occupies **team slot 1** as a Ghost
	 * (Adrien: 2026-09-29 — {@code /phantasmon sendout} no longer takes a UUID
	 * argument; typing a UUID by hand every time was the exact "très chiant"
	 * pain point that motivated this, same as the trade UUID complaint). Shared
	 * by both that command and the new {@code sendout} keybind — neither needs
	 * a {@link FabricClientCommandSource} anymore since there's no argument to
	 * parse, so feedback goes straight to chat like {@link #openPc()}.
	 * {@code onFound} is only invoked on success (main client thread), letting
	 * the actual {@code GhostSession.sendOut(UUID)} call stay outside this
	 * class — {@code pokemon} has no reason to depend on {@code ghost}.
	 */
	public void sendOutTeamLead(java.util.function.Consumer<UUID> onFound) {
		if (!session.isAuthenticated()) {
			chatMessage(Component.translatable("phantasmon.error.not_authenticated"));
			return;
		}
		pokemonClient.listForOwner(session.accessToken(), session.playerUuid())
				.thenAccept(pokemons -> {
					java.util.Optional<PokemonDto> lead = java.util.Arrays.stream(pokemons)
							.filter(pokemon -> pokemon.teamSlot() != null && pokemon.teamSlot() == 1)
							.findFirst();
					if (lead.isEmpty()) {
						chatMessage(Component.translatable("phantasmon.ghost.error.no_team_lead"));
						return;
					}
					UUID uuid = lead.get().uuid();
					Minecraft.getInstance().execute(() -> onFound.accept(uuid));
				})
				.exceptionally(ex -> {
					Throwable cause = ex instanceof CompletionException ? ex.getCause() : ex;
					String key = cause instanceof BackendApiException apiException
							? BackendErrorMessages.translationKey(apiException.errorCode())
							: "phantasmon.error.network";
					Minecraft.getInstance().execute(() -> chatMessage(Component.translatable(key)));
					return null;
				});
	}

	private static void chatMessage(Component message) {
		var player = Minecraft.getInstance().player;
		if (player != null) {
			player.displayClientMessage(message, false);
		}
	}

	/** Called once per client tick (see {@link #openPc}) — opens the PC screen if one was requested. */
	public void tick() {
		if (pcScreenRequested) {
			pcScreenRequested = false;
			Minecraft.getInstance().setScreen(new PhantasmonPcScreen(pokemonClient, session));
		}
	}

	private boolean requireAuthenticated(FabricClientCommandSource source) {
		if (!session.isAuthenticated()) {
			source.sendError(Component.translatable("phantasmon.error.not_authenticated"));
			return false;
		}
		return true;
	}

	/**
	 * Shows the <b>full</b> UUID as plain visible text — commands need the
	 * complete UUID, not the shortened form this used to show (Adrien hit this:
	 * typing the truncated text by hand fails Brigadier's UUID parser). Clicking
	 * it also pre-fills the chat box with the raw UUID (not a full command,
	 * since we don't know which action the player wants next) as a copy-paste
	 * shortcut on top of the visible text, not instead of it.
	 */
	private static MutableComponent summaryLine(PokemonDto pokemon) {
		String label = pokemon.species() + (pokemon.form() != null ? "-" + pokemon.form() : "")
				+ " (Lv." + pokemon.level() + ") ";
		MutableComponent uuidPart = Component.literal(pokemon.uuid().toString())
				.withStyle(ChatFormatting.GRAY, ChatFormatting.UNDERLINE)
				.withStyle(style -> style.withClickEvent(
						new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, pokemon.uuid().toString())));
		return Component.literal(label).append(uuidPart);
	}

	private static void reportFailure(FabricClientCommandSource source, Throwable throwable, String context) {
		Throwable cause = throwable instanceof CompletionException ? throwable.getCause() : throwable;
		String translationKey = cause instanceof BackendApiException apiException
				? BackendErrorMessages.translationKey(apiException.errorCode())
				: "phantasmon.error.network";
		Component message = context == null
				? Component.translatable(translationKey)
				: Component.translatable(translationKey).append(" (" + context + ")");
		Minecraft.getInstance().execute(() -> source.sendError(message));
	}

	private static void feedback(FabricClientCommandSource source, Component message) {
		Minecraft.getInstance().execute(() -> source.sendFeedback(message));
	}
}
