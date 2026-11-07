package com.mystaria.phantasmon.client.pokemon.showdown;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

/** A full PokéPaste team: six sets, trailing spaces and several blank lines between them. */
class ShowdownFullTeamTest {

	private static final String TEAM = "Ninetales @ Heat Rock  \nAbility: Drought  \nTera Type: Fire  \nEVs: 252 SpA / 4 SpD / 252 Spe  \nTimid Nature  \nIVs: 0 Atk  \n- Overheat  \n- Scorching Sands  \n- Healing Wish  \n- Encore  \n\n\n"
			+ "Venusaur @ Life Orb  \nAbility: Chlorophyll  \nTera Type: Ground  \nEVs: 4 Def / 252 SpA / 252 Spe  \nModest Nature  \nIVs: 0 Atk  \n- Growth  \n- Weather Ball  \n- Sludge Bomb  \n- Earth Power  \n\n\n"
			+ "Cresselia @ Eject Button  \nAbility: Levitate  \nShiny: Yes  \nTera Type: Steel  \nEVs: 252 HP / 200 Def / 56 SpD  \nCalm Nature  \nIVs: 0 Atk  \n- Ice Beam  \n- Moonlight  \n- Lunar Dance  \n- Thunder Wave  \n\n\n"
			+ "Great Tusk @ Eject Pack  \nAbility: Protosynthesis  \nTera Type: Ground  \nEVs: 252 Atk / 4 SpD / 252 Spe  \nJolly Nature  \n- Headlong Rush  \n- Close Combat  \n- Rapid Spin  \n- Ice Spinner  \n\n\n"
			+ "Dragonite @ Dragonium Z  \nAbility: Multiscale  \nTera Type: Dragon  \nEVs: 252 Atk / 4 Def / 252 Spe  \nJolly Nature  \n- Dragon Dance  \n- Outrage  \n- Earthquake  \n- Fire Punch  \n\n\n"
			+ "Diancie @ Diancite  \nAbility: Clear Body  \nTera Type: Rock  \nEVs: 4 Atk / 252 SpA / 252 Spe  \nHasty Nature  \n- Stealth Rock  \n- Mystical Fire  \n- Diamond Storm  \n- Moonblast  \n";

	@Test
	void parsesAndMapsTheSixSets() {
		List<ShowdownPokemon> team = ShowdownParser.parseTeam(TEAM);
		assertEquals(6, team.size());
		assertEquals(List.of("ninetales", "venusaur", "cresselia", "greattusk", "dragonite", "diancie"),
				team.stream().map(set -> ShowdownImportMapper.toCreateRequest(set, "1.8.1").species()).toList());
	}
}
