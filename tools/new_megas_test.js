// Plays the new Mega Evolutions (new_megas.js + new_megas_data.js) in the battle engine Cobblemon bundles, in Node.
//
//   node tools/new_megas_test.js <folder where Cobblemon's data/cobblemon/showdown.zip is extracted>
//
// Every new forme must Mega Evolve from its base forme with its stone (types, stats, ability of the Mega), and the
// four mechanics the installer adds to the old engine must work. Exits with 1 on any failure.
'use strict';
const fs = require('fs');
const path = require('path');
const {createRequire} = require('module');

const dir = path.resolve(process.argv[2] || '');
if (!fs.existsSync(path.join(dir, 'sim', 'battle.js'))) {
	console.error('Usage: node tools/new_megas_test.js <extracted showdown folder>');
	process.exit(2);
}
const req = createRequire(path.join(dir, 'index.js'));
const {Dex} = req('./sim/dex');
const {Cobblemon} = req('./sim/cobblemon/cobblemon');
const {Battle} = req('./sim/battle');

const root = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'phantasmon', 'showdown');
const DATA = eval(fs.readFileSync(path.join(root, 'new_megas_data.js'), 'utf8'));
const install = new Function('require', 'Dex', 'Cobblemon',
	'return (\n' + fs.readFileSync(path.join(root, 'new_megas.js'), 'utf8') + '\n);')(req, Dex, Cobblemon);
const added = install(DATA);
console.log(`Installed ${added.species.length} formes, ${added.items.length} stones, ${added.abilities.length} abilities`);

let failures = 0;
function check(ok, what) {
	if (!ok) {
		failures++;
		console.log('FAIL ' + what);
	}
}

function set(species, item, moves, extra) {
	const s = Dex.species.get(species);
	return Object.assign({
		name: s.name, species: s.name, item, ability: s.abilities[0], moves, nature: 'Hardy', level: 50,
		gender: s.gender || '', evs: {hp: 0, atk: 0, def: 0, spa: 0, spd: 0, spe: 0},
		ivs: {hp: 31, atk: 31, def: 31, spa: 31, spd: 31, spe: 31},
		movesInfo: moves.map(() => ({pp: 32, maxPp: 32})),
	}, extra || {});
}

/** A single battle, p1 then p2; turns = [[p1 choice, p2 choice], ...]. Returns the battle. */
function play(p1, p2, turns) {
	const battle = new Battle({formatid: 'gen9customgame', seed: [1, 2, 3, 4]});
	battle.setPlayer('p1', {name: 'A', team: [p1]});
	battle.setPlayer('p2', {name: 'B', team: [p2]});
	if (battle.requestState === 'teampreview') battle.makeChoices('default', 'default');
	for (const [c1, c2] of turns) battle.makeChoices(c1, c2);
	return battle;
}

const foe = set('Chansey', '', ['splash'], {ability: 'Natural Cure'});

// Every forme Mega Evolves from its base with its stone.
for (const id of Object.keys(DATA.species)) {
	const mega = Dex.species.get(id);
	check(mega.exists, `${id} unknown to the Dex`);
	// Its base forme: the one its stone names for it.
	const stone = DATA.items[Dex.toID(mega.requiredItem)].megaStone;
	const base = Object.keys(stone).find((name) => stone[name] === mega.name);
	if (Dex.species.get(base).battleOnly) continue; // Zygarde-Complete, only reached mid-battle: tested below
	let battle;
	try {
		battle = play(set(base, mega.requiredItem, ['splash']), foe, [['move 1 mega', 'move 1']]);
	} catch (e) {
		check(false, `${mega.name}: ${e.stack}`);
		continue;
	}
	const pokemon = battle.p1.active[0];
	check(pokemon.species.name === mega.name, `${mega.name}: became ${pokemon.species.name}`);
	// The species' own types and ability: Protean and Trace change the live ones at once.
	check(pokemon.species.types.join('/') === mega.types.join('/'), `${mega.name}: types ${pokemon.species.types}`);
	check(pokemon.baseAbility === Dex.toID(mega.abilities[0]), `${mega.name}: ability ${pokemon.baseAbility}`);
	check(battle.log.some((l) => l.startsWith('|-mega|')), `${mega.name}: no -mega line`);
}

// Zygarde: Power Construct makes it Complete below half HP, then it may Mega Evolve.
{
	const battle = play(set('Zygarde', 'Zygardite', ['splash'], {ability: 'Power Construct'}), foe,
		[['move 1', 'move 1']]);
	const zygarde = battle.p1.active[0];
	zygarde.sethp(1);
	battle.makeChoices('move 1', 'move 1');
	check(zygarde.species.name === 'Zygarde-Complete', 'Zygarde: not Complete but ' + zygarde.species.name);
	try {
		battle.makeChoices('move 1 mega', 'move 1');
	} catch (e) {
		check(false, 'Zygarde-Complete cannot Mega Evolve: ' + e.message);
	}
	check(zygarde.species.name === 'Zygarde-Mega', 'Zygarde: became ' + zygarde.species.name);
}
// A stone with several formes: Meowstic-F gets the F Mega.
{
	const battle = play(set('Meowstic-F', 'Meowsticite', ['splash']), foe, [['move 1 mega', 'move 1']]);
	check(battle.p1.active[0].species.name === 'Meowstic-F-Mega', 'Meowstic-F: ' + battle.p1.active[0].species.name);
}
// A stone on the wrong species does nothing.
{
	const battle = play(set('Pikachu', 'Clefablite', ['splash']), foe, [['move 1', 'move 1']]);
	check(!battle.p1.active[0].canMegaEvo, 'Pikachu can Mega Evolve with Clefablite');
}
// Mega Sol: Solar Beam fires at once, no sunlight on the field.
{
	const battle = play(set('Meganium', 'Meganiumite', ['solarbeam']), foe, [['move 1 mega', 'move 1']]);
	check(!battle.p1.active[0].volatiles['twoturnmove'], 'Mega Sol: Solar Beam charged');
	check(battle.p2.active[0].hp < battle.p2.active[0].maxhp, 'Mega Sol: Solar Beam did no damage');
}
// Eelevate: Earthquake doesn't touch it.
{
	const battle = play(set('Eelektross', 'Eelektrossite', ['splash']), set('Garchomp', '', ['earthquake']),
		[['move 1 mega', 'move 1']]);
	check(battle.log.some((l) => l.startsWith('|-immune|p1a') && l.includes('Eelevate')), 'Eelevate: not immune to Ground');
	check(battle.p1.active[0].hp === battle.p1.active[0].maxhp, 'Eelevate: took Ground damage');
}
// Piercing Drill: a contact move goes through Protect for a quarter.
{
	const battle = play(set('Excadrill', 'Excadrite', ['ironhead']), set('Chansey', '', ['protect']),
		[['move 1 mega', 'move 1']]);
	const target = battle.p2.active[0];
	check(target.hp < target.maxhp, 'Piercing Drill: Protect blocked the hit');
	check(battle.log.some((l) => l.startsWith('|-zbroken|')), 'Piercing Drill: no -zbroken line');
}
// Dragonize: Tackle turns Dragon (Clefable is immune).
{
	const battle = play(set('Feraligatr', 'Feraligite', ['tackle']), set('Clefable', '', ['splash']),
		[['move 1 mega', 'move 1']]);
	check(battle.log.some((l) => l.startsWith('|-immune|p2a')), 'Dragonize: Tackle stayed Normal');
}
// Spicy Spray: a hit burns the attacker.
{
	const battle = play(set('Scovillain', 'Scovillainite', ['splash']), set('Tauros', '', ['tackle']),
		[['move 1 mega', 'move 1']]);
	check(battle.p2.active[0].status === 'brn', 'Spicy Spray: attacker not burnt');
}
// Fire Mane and Aura Guard: they run (their code is Showdown's own).
for (const [base, stone, move, foeMove] of [['Pyroar', 'Pyroarite', 'flamethrower', 'splash'],
	['Lucario', 'Lucarionite Z', 'splash', 'tackle']]) {
	try {
		play(set(base, stone, [move]), set('Tauros', '', [foeMove]), [['move 1 mega', 'move 1']]);
	} catch (e) {
		check(false, `${base}: ${e.stack}`);
	}
}
// Installing twice adds nothing (the engine already knows them).
check(install(DATA).species.length === 0, 'second install added formes again');

console.log(failures ? `${failures} failure(s)` : 'All new Megas OK');
process.exit(failures ? 1 : 0);
