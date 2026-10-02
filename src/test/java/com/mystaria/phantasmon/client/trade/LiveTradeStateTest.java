package com.mystaria.phantasmon.client.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Payloads below mimic what {@code PhantasmonWebSocketClient} actually hands
 * over: Gson-parsed maps, where every JSON number is a {@code Double}.
 */
class LiveTradeStateTest {

	private static final UUID SESSION = UUID.randomUUID();
	private static final UUID PARTNER = UUID.randomUUID();
	private static final UUID OWN_A = UUID.randomUUID();
	private static final UUID OWN_B = UUID.randomUUID();
	private static final UUID PARTNER_A = UUID.randomUUID();

	private static Map<String, Object> pokemon(UUID uuid, String species, Double teamSlot) {
		Map<String, Object> pokemon = new HashMap<>();
		pokemon.put("uuid", uuid.toString());
		pokemon.put("owner_uuid", UUID.randomUUID().toString());
		pokemon.put("species", species);
		pokemon.put("level", 50.0);
		pokemon.put("nature", "timid");
		pokemon.put("ability", "static");
		pokemon.put("is_shiny", true);
		pokemon.put("team_slot", teamSlot);
		pokemon.put("data", Map.of("gender", "F", "moves", List.of("thunderbolt")));
		return pokemon;
	}

	private static LiveTradeState started() {
		Map<String, Object> data = new HashMap<>();
		data.put("session_uuid", SESSION.toString());
		data.put("partner_uuid", PARTNER.toString());
		data.put("partner_name", "Jason");
		data.put("own_team", List.of(pokemon(OWN_A, "pikachu", 4.0), pokemon(OWN_B, "eevee", 2.0)));
		data.put("partner_team", List.of(pokemon(PARTNER_A, "charmander", 1.0)));
		return LiveTradeState.fromSessionStarted(data);
	}

	private static Map<String, Object> update(UUID ownOffer, UUID partnerOffer, boolean ownReady, boolean partnerReady) {
		Map<String, Object> data = new HashMap<>();
		data.put("session_uuid", SESSION.toString());
		data.put("own_offer", ownOffer == null ? null : ownOffer.toString());
		data.put("partner_offer", partnerOffer == null ? null : partnerOffer.toString());
		data.put("own_ready", ownReady);
		data.put("partner_ready", partnerReady);
		return data;
	}

	@Test
	void teamsAreLaidOutByTheirRealTeamSlotWithHolesKept() {
		LiveTradeState state = started();

		assertNull(state.ownSlot(0));
		assertEquals(OWN_B, state.ownSlot(1).uuid());
		assertEquals(OWN_A, state.ownSlot(3).uuid());
		assertEquals("Jason", state.partnerName());
		assertEquals(50, state.ownSlot(3).level());
		assertTrue(state.ownSlot(3).isShiny());
		assertEquals("F", state.ownSlot(3).data().get("gender"));
	}

	@Test
	void defaultOfferIsTheFirstNonEmptySlot() {
		assertEquals(OWN_B, started().firstOwnPokemon().uuid());
	}

	@Test
	void updateReflectsOffersAndReadyFlags() {
		LiveTradeState state = started();

		state.applyUpdate(update(OWN_A, PARTNER_A, true, false));

		assertEquals(3, state.ownOfferIndex());
		assertEquals(0, state.partnerOfferIndex());
		assertEquals("charmander", state.partnerOfferPokemon().species());
		assertTrue(state.ownReady());
		assertFalse(state.partnerReady());
		assertTrue(state.bothOffersChosen());
	}

	@Test
	void updateForAnotherSessionIsIgnored() {
		LiveTradeState state = started();
		Map<String, Object> foreign = update(OWN_A, PARTNER_A, true, true);
		foreign.put("session_uuid", UUID.randomUUID().toString());

		state.applyUpdate(foreign);

		assertNull(state.ownOffer());
		assertFalse(state.ownReady());
	}

	@Test
	void anUpdateClearsTheLastError() {
		LiveTradeState state = started();
		state.setLastErrorCode("ERROR_TRADE_OFFER_NOT_IN_TEAM");

		state.applyUpdate(update(OWN_A, null, false, false));

		assertNull(state.lastErrorCode());
	}

	@Test
	void completionResolvesGivenAndReceivedPokemon() {
		LiveTradeState state = started();
		state.applyUpdate(update(OWN_A, PARTNER_A, true, true));

		state.markCompleted(Map.of("session_uuid", SESSION.toString(),
				"given_pokemon", OWN_A.toString(), "received_pokemon", PARTNER_A.toString()));

		assertEquals(LiveTradeState.Phase.COMPLETED, state.phase());
		assertEquals("pikachu", state.givenPokemon().species());
		assertEquals("charmander", state.receivedPokemon().species());
	}

	@Test
	void cancellationKeepsTheReasonAndFreezesTheState() {
		LiveTradeState state = started();

		state.markCancelled(Map.of("session_uuid", SESSION.toString(), "reason", "PARTNER_LEFT"));
		state.applyUpdate(update(OWN_A, PARTNER_A, true, true));

		assertEquals(LiveTradeState.Phase.CANCELLED, state.phase());
		assertEquals("PARTNER_LEFT", state.cancelReason());
		assertNull(state.ownOffer(), "no update applies once the session is over");
	}

	@Test
	void malformedStartIsRejected() {
		assertNull(LiveTradeState.fromSessionStarted(Map.of("partner_name", "Jason")));
	}
}
