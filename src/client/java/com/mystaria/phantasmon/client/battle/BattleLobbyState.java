package com.mystaria.phantasmon.client.battle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import com.mystaria.phantasmon.client.pokemon.PokemonDto;

/**
 * The battle lobby as the backend shows it to this player ({@code BattleLobbyUpdated}): our own team in full with
 * our lead, the opponent's team as a preview only (species, form, shininess, gender — what the model shows) and
 * whether they are ready. Their lead is never sent. Everything here is the server's; the screen only reads it.
 */
public final class BattleLobbyState {

	public static final int TEAM_SIZE = 6;
	private static final Gson GSON = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();

	private final UUID lobbyUuid;
	private UUID opponentUuid;
	private String opponentName = "?";
	private String ownSource = "GHOST";
	private String opponentSource = "GHOST";
	private List<PokemonDto> ownTeam = List.of();
	private List<PokemonDto> opponentTeam = List.of();
	private int ownLead;
	private boolean ownReady;
	private boolean opponentReady;
	private int timerTotalSeconds = 150;
	/** Local clock deadline of the lobby timer, 0 while off. */
	private long timerDeadline;
	private String timerByName;
	private String lastErrorCode;

	BattleLobbyState(UUID lobbyUuid) {
		this.lobbyUuid = lobbyUuid;
	}

	void apply(Map<String, Object> data) {
		opponentUuid = parseUuid(data.get("opponent_uuid"));
		opponentName = data.get("opponent_name") == null ? "?" : data.get("opponent_name").toString();
		ownSource = String.valueOf(data.get("own_team_source"));
		opponentSource = String.valueOf(data.get("opponent_team_source"));
		ownTeam = ownTeam(data.get("own_team"));
		opponentTeam = preview(data.get("opponent_team"));
		ownLead = data.get("own_lead") instanceof Number lead ? lead.intValue() : 0;
		ownReady = Boolean.TRUE.equals(data.get("own_ready"));
		opponentReady = Boolean.TRUE.equals(data.get("opponent_ready"));
		if (data.get("timer_total_seconds") instanceof Number total) {
			timerTotalSeconds = total.intValue();
		}
		timerDeadline = data.get("timer_seconds_left") instanceof Number left ? System.currentTimeMillis() + left.longValue() * 1000L : 0;
		timerByName = data.get("timer_by_name") == null ? null : data.get("timer_by_name").toString();
		lastErrorCode = null;
	}

	/** Kept in the backend's order: lead indexes refer to it. */
	private static List<PokemonDto> ownTeam(Object raw) {
		List<PokemonDto> team = new ArrayList<>();
		if (raw instanceof List<?> list) {
			for (Object entry : list) {
				PokemonDto pokemon = GSON.fromJson(GSON.toJsonTree(entry), PokemonDto.class);
				if (pokemon != null) {
					team.add(pokemon);
				}
			}
		}
		return team;
	}

	/** The opponent's preview, as Pokémon with nothing but what the model needs (level 0, no set, no item). */
	private static List<PokemonDto> preview(Object raw) {
		List<PokemonDto> team = new ArrayList<>();
		if (raw instanceof List<?> list) {
			for (Object entry : list) {
				if (entry instanceof Map<?, ?> map && map.get("species") != null) {
					Map<String, Object> data = new HashMap<>();
					if (map.get("gender") != null) {
						data.put("gender", map.get("gender"));
					}
					team.add(new PokemonDto(null, null, map.get("species").toString(),
							map.get("form") == null ? null : map.get("form").toString(), 0, null, null,
							Boolean.TRUE.equals(map.get("is_shiny")), null, null, null, null, data));
				}
			}
		}
		return team;
	}

	private static UUID parseUuid(Object raw) {
		try {
			return raw == null ? null : UUID.fromString(raw.toString());
		} catch (IllegalArgumentException ex) {
			return null;
		}
	}

	public UUID lobbyUuid() {
		return lobbyUuid;
	}

	public UUID opponentUuid() {
		return opponentUuid;
	}

	public String opponentName() {
		return opponentName;
	}

	public boolean ownIsCobblemon() {
		return "COBBLEMON".equals(ownSource);
	}

	public boolean opponentIsCobblemon() {
		return "COBBLEMON".equals(opponentSource);
	}

	public PokemonDto ownSlot(int index) {
		return index >= 0 && index < ownTeam.size() ? ownTeam.get(index) : null;
	}

	public PokemonDto opponentSlot(int index) {
		return index >= 0 && index < opponentTeam.size() ? opponentTeam.get(index) : null;
	}

	public int opponentTeamSize() {
		return opponentTeam.size();
	}

	public int ownTeamSize() {
		return ownTeam.size();
	}

	public int ownLead() {
		return ownLead;
	}

	public PokemonDto ownLeadPokemon() {
		return ownSlot(ownLead);
	}

	public boolean ownReady() {
		return ownReady;
	}

	public boolean opponentReady() {
		return opponentReady;
	}

	public boolean timerOn() {
		return timerDeadline > 0;
	}

	public int timerTotalSeconds() {
		return timerTotalSeconds;
	}

	public long timerSecondsLeft() {
		return timerDeadline <= 0 ? 0 : Math.max(0, (timerDeadline - System.currentTimeMillis() + 999) / 1000);
	}

	public String timerByName() {
		return timerByName;
	}

	public String lastErrorCode() {
		return lastErrorCode;
	}

	void setLastErrorCode(String errorCode) {
		lastErrorCode = errorCode;
	}
}
