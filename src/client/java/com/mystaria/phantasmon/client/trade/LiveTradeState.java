package com.mystaria.phantasmon.client.trade;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import com.mystaria.phantasmon.client.pokemon.PokemonDto;

/**
 * Client-side mirror of one live trade session (the graphical trade screen,
 * Adrien 2026-10-02), rebuilt purely from the backend's WebSocket events:
 * {@code TradeSessionStarted} creates it, {@code TradeSessionUpdate} /
 * {@code TradeSessionCompleted} / {@code TradeSessionCancelled} mutate it.
 * The backend stays the only authority (offers, ready flags and the final
 * swap are all decided server-side) — this class never changes state on its
 * own initiative, it only reflects what the server said.
 *
 * <p>Pure logic, no Minecraft import, so it's unit-tested like
 * {@code VersionCompatibility}. Only touched from the client thread.
 */
public final class LiveTradeState {

	public static final int TEAM_SIZE = 6;

	public enum Phase { ACTIVE, COMPLETED, CANCELLED }

	/** Same snake_case convention as every other backend payload, so the WS maps convert straight into {@link PokemonDto}. */
	private static final Gson GSON = new GsonBuilder()
			.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
			.create();

	private final UUID sessionUuid;
	private final UUID partnerUuid;
	private final String partnerName;
	private final PokemonDto[] ownTeam;
	private final PokemonDto[] partnerTeam;

	private UUID ownOffer;
	private UUID partnerOffer;
	private boolean ownReady;
	private boolean partnerReady;
	private Phase phase = Phase.ACTIVE;
	private String cancelReason;
	private UUID givenPokemon;
	private UUID receivedPokemon;
	private String lastErrorCode;

	private LiveTradeState(UUID sessionUuid, UUID partnerUuid, String partnerName, PokemonDto[] ownTeam, PokemonDto[] partnerTeam) {
		this.sessionUuid = sessionUuid;
		this.partnerUuid = partnerUuid;
		this.partnerName = partnerName;
		this.ownTeam = ownTeam;
		this.partnerTeam = partnerTeam;
	}

	/** Builds the state from a {@code TradeSessionStarted} payload; {@code null} if it's malformed. */
	public static LiveTradeState fromSessionStarted(Map<String, Object> data) {
		UUID sessionUuid = uuid(data.get("session_uuid"));
		UUID partnerUuid = uuid(data.get("partner_uuid"));
		if (sessionUuid == null || partnerUuid == null) {
			return null;
		}
		Object name = data.get("partner_name");
		return new LiveTradeState(sessionUuid, partnerUuid, name == null ? "?" : name.toString(),
				team(data.get("own_team")), team(data.get("partner_team")));
	}

	public boolean isSameSession(Map<String, Object> data) {
		return sessionUuid.equals(uuid(data.get("session_uuid")));
	}

	/** {@code TradeSessionUpdate}: the server's own/partner perspective is already resolved for us. */
	public void applyUpdate(Map<String, Object> data) {
		if (!isSameSession(data) || phase != Phase.ACTIVE) {
			return;
		}
		ownOffer = uuid(data.get("own_offer"));
		partnerOffer = uuid(data.get("partner_offer"));
		ownReady = Boolean.TRUE.equals(data.get("own_ready"));
		partnerReady = Boolean.TRUE.equals(data.get("partner_ready"));
		lastErrorCode = null;
	}

	public void markCompleted(Map<String, Object> data) {
		if (!isSameSession(data)) {
			return;
		}
		phase = Phase.COMPLETED;
		givenPokemon = uuid(data.get("given_pokemon"));
		receivedPokemon = uuid(data.get("received_pokemon"));
	}

	public void markCancelled(Map<String, Object> data) {
		if (!isSameSession(data)) {
			return;
		}
		phase = Phase.CANCELLED;
		Object reason = data.get("reason");
		cancelReason = reason == null ? null : reason.toString();
	}

	/** A {@code TradeSessionError} rejection (e.g. offering a PC Pokémon) — shown on screen until the next update. */
	public void setLastErrorCode(String errorCode) {
		this.lastErrorCode = errorCode;
	}

	public UUID sessionUuid() {
		return sessionUuid;
	}

	public UUID partnerUuid() {
		return partnerUuid;
	}

	public String partnerName() {
		return partnerName;
	}

	/** Team slot {@code index} (0-5) of the local player, {@code null} if empty. */
	public PokemonDto ownSlot(int index) {
		return ownTeam[index];
	}

	public PokemonDto partnerSlot(int index) {
		return partnerTeam[index];
	}

	public UUID ownOffer() {
		return ownOffer;
	}

	public UUID partnerOffer() {
		return partnerOffer;
	}

	public PokemonDto ownOfferPokemon() {
		return find(ownTeam, ownOffer);
	}

	public PokemonDto partnerOfferPokemon() {
		return find(partnerTeam, partnerOffer);
	}

	/** Rail index (0-5) of the current offer, -1 if none. */
	public int ownOfferIndex() {
		return indexOf(ownTeam, ownOffer);
	}

	public int partnerOfferIndex() {
		return indexOf(partnerTeam, partnerOffer);
	}

	/** First non-empty team slot — the default offer picked when the screen opens (spec §7.1: "slot 0 sélectionné par défaut"). */
	public PokemonDto firstOwnPokemon() {
		for (PokemonDto pokemon : ownTeam) {
			if (pokemon != null) {
				return pokemon;
			}
		}
		return null;
	}

	public boolean ownReady() {
		return ownReady;
	}

	public boolean partnerReady() {
		return partnerReady;
	}

	public boolean bothOffersChosen() {
		return ownOffer != null && partnerOffer != null;
	}

	public Phase phase() {
		return phase;
	}

	public String cancelReason() {
		return cancelReason;
	}

	public PokemonDto givenPokemon() {
		return find(ownTeam, givenPokemon);
	}

	public PokemonDto receivedPokemon() {
		return find(partnerTeam, receivedPokemon);
	}

	public String lastErrorCode() {
		return lastErrorCode;
	}

	/**
	 * Lays the team out by {@code team_slot} (1-6 → index 0-5) so the rail
	 * keeps the player's real slot order with holes left empty; anything
	 * without a usable slot falls into the first free index instead of being
	 * dropped.
	 */
	private static PokemonDto[] team(Object raw) {
		PokemonDto[] slots = new PokemonDto[TEAM_SIZE];
		if (!(raw instanceof List<?> list)) {
			return slots;
		}
		List<PokemonDto> unplaced = new java.util.ArrayList<>();
		for (Object entry : list) {
			PokemonDto pokemon = GSON.fromJson(GSON.toJsonTree(entry), PokemonDto.class);
			if (pokemon == null || pokemon.uuid() == null) {
				continue;
			}
			Integer slot = pokemon.teamSlot();
			if (slot != null && slot >= 1 && slot <= TEAM_SIZE && slots[slot - 1] == null) {
				slots[slot - 1] = pokemon;
			} else {
				unplaced.add(pokemon);
			}
		}
		for (PokemonDto pokemon : unplaced) {
			for (int i = 0; i < TEAM_SIZE; i++) {
				if (slots[i] == null) {
					slots[i] = pokemon;
					break;
				}
			}
		}
		return slots;
	}

	private static PokemonDto find(PokemonDto[] team, UUID uuid) {
		int index = indexOf(team, uuid);
		return index < 0 ? null : team[index];
	}

	private static int indexOf(PokemonDto[] team, UUID uuid) {
		if (uuid == null) {
			return -1;
		}
		for (int i = 0; i < team.length; i++) {
			if (team[i] != null && uuid.equals(team[i].uuid())) {
				return i;
			}
		}
		return -1;
	}

	private static UUID uuid(Object raw) {
		if (raw == null) {
			return null;
		}
		try {
			return UUID.fromString(raw.toString());
		} catch (IllegalArgumentException ex) {
			return null;
		}
	}
}
