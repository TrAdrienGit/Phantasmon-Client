// Installs the Mega Evolutions of new_megas_data.js (Legends Z-A / Champions) in Cobblemon's battle engine, which is
// older than them (2026-11-26). Called with that data; evaluated by NewMegas.java in the Showdown context, and by
// tools/new_megas_test.js in Node.
//
// Only what the engine doesn't know is added: once an update of Cobblemon (or a mod) brings these Megas, its own
// formes, stones and abilities play and this one steps aside.
//
// The bundled engine lacks four things the newer Showdown has; they are added here:
//  - a Mega Stone naming several formes (Meowsticite, Tatsugirinite, Magearnite): canMegaEvo picks the forme;
//  - Mega Sol: moves act as in harsh sunlight (Pokemon.effectiveWeather);
//  - Eelevate: levitates like Levitate (Pokemon.isGrounded / runImmunity);
//  - Piercing Drill: contact moves hit through Protect for a quarter of the damage (no HitProtect event here);
//  - Zygarde-Complete, reached mid-battle, may still Mega Evolve (Power Construct asks again in the newer engine).
(function (DATA) {
	const D = (typeof Dex !== 'undefined') ? Dex : require('./sim/dex').Dex;
	const C = (typeof Cobblemon !== 'undefined') ? Cobblemon : require('./sim/cobblemon/cobblemon').Cobblemon;
	const BattleActions = require('./sim/battle-actions').BattleActions;
	const Pokemon = require('./sim/pokemon').Pokemon;

	function known(table, id) {
		try {
			return !!table.get(id).exists;
		} catch (e) {
			return false;
		}
	}

	/**
	 * Piercing Drill without the newer engine's HitProtect event: before Protect (priority 3) sees the move, the
	 * attacker's ability takes the move's protect flag off for a protected target, then the hit does a quarter.
	 */
	const PIERCING_DRILL = {
		onSourceTryHitPriority: 4,
		onSourceTryHit(target, source, move) {
			if (!move.flags['contact'] || !move.flags['protect'] || !target.isProtected()) return;
			move.flags = Object.assign({}, move.flags);
			delete move.flags['protect'];
			target.getMoveHitData(move).piercingDrill = true;
		},
		onModifyDamage(damage, source, target, move) {
			if (!target.getMoveHitData(move).piercingDrill) return;
			this.add('-ability', source, 'Piercing Drill');
			this.add('-zbroken', target);
			return this.chainModify(0.25);
		},
	};

	// --- Formes, stones, abilities ---------------------------------------------------------------------------------

	const added = {species: [], items: [], abilities: []};
	for (const [id, data] of Object.entries(DATA.abilities)) {
		if (known(D.abilities, id)) continue;
		const ability = Object.assign({}, data);
		if (id === 'piercingdrill') Object.assign(ability, PIERCING_DRILL);
		C.registries.ability.register(ability, id);
		added.abilities.push(id);
	}
	for (const [id, data] of Object.entries(DATA.species)) {
		if (known(D.species, id)) continue;
		C.registries.species.register(Object.assign({}, data));
		added.species.push(id);
	}
	for (const [id, data] of Object.entries(DATA.items)) {
		if (known(D.items, id)) continue;
		// The newer Showdown maps each base forme to its Mega; this engine wants one string (megaStone) and its base
		// species (megaEvolves). Both are kept: the string for the engine's own checks, the map for canMegaEvo below.
		const formes = data.megaStone;
		const first = Object.keys(formes)[0];
		C.registries.heldItem.register(Object.assign({}, data, {
			megaStones: formes,
			megaStone: formes[first],
			megaEvolves: D.species.get(first).baseSpecies,
			onTakeItem(item, source) {
				const bases = Object.keys(item.megaStones).map((name) => this.dex.species.get(name).baseSpecies);
				return !bases.includes(source.baseSpecies.baseSpecies);
			},
		}), id);
		added.items.push(id);
	}
	C.registries.ability.invalidate();
	C.registries.species.invalidate();
	C.registries.heldItem.invalidate();

	// --- What the bundled engine lacks (patched once per engine) ---------------------------------------------------

	if (!BattleActions.prototype.phantasmonNewMegas) {
		BattleActions.prototype.phantasmonNewMegas = true;

		const canMegaEvo = BattleActions.prototype.canMegaEvo;
		BattleActions.prototype.canMegaEvo = function (pokemon) {
			const formes = pokemon.getItem().megaStones;
			if (!formes) return canMegaEvo.call(this, pokemon);
			const species = pokemon.baseSpecies;
			const mega = formes[species.name] || formes[species.baseSpecies];
			return mega && mega !== species.name ? mega : null;
		};

		// Zygarde-Complete (Power Construct, mid-battle) Mega Evolves with Zygardite: the newer engine asks again once
		// the forme is reached, unless the side already Mega Evolved.
		const runMegaEvo = BattleActions.prototype.runMegaEvo;
		BattleActions.prototype.runMegaEvo = function (pokemon) {
			const evolved = runMegaEvo.call(this, pokemon);
			if (evolved && pokemon.canMegaEvo !== undefined) pokemon.side.phantasmonMegaUsed = true;
			return evolved;
		};
		const formeChange = Pokemon.prototype.formeChange;
		Pokemon.prototype.formeChange = function (speciesId, source, isPermanent, message) {
			const changed = formeChange.call(this, speciesId, source, isPermanent, message);
			if (changed && isPermanent && this.species.id === 'zygardecomplete' && !this.side.phantasmonMegaUsed) {
				this.canMegaEvo = this.battle.actions.canMegaEvo(this);
			}
			return changed;
		};

		const effectiveWeather = Pokemon.prototype.effectiveWeather;
		Pokemon.prototype.effectiveWeather = function (sourceEffect, message) {
			if (!sourceEffect && this.battle.effect) sourceEffect = this.battle.effect;
			const active = this.battle.activePokemon;
			if (active && active.hasAbility('megasol') && sourceEffect &&
				(sourceEffect.id === 'megasol' || sourceEffect.effectType === 'Move' || sourceEffect.effectType === 'Weather')) {
				if (this.battle.field.effectiveWeather() !== 'sunnyday' && message) {
					this.battle.add('-activate', this, 'ability: Mega Sol');
				}
				return 'sunnyday';
			}
			return effectiveWeather.call(this);
		};

		const isGrounded = Pokemon.prototype.isGrounded;
		Pokemon.prototype.isGrounded = function (negateImmunity = false) {
			if (!this.hasAbility('eelevate') || this.battle.suppressingAbility(this)) {
				return isGrounded.call(this, negateImmunity);
			}
			// The engine's own order, Eelevate standing where Levitate does.
			if ('gravity' in this.battle.field.pseudoWeather) return true;
			if ('ingrain' in this.volatiles && this.battle.gen >= 4) return true;
			if ('smackdown' in this.volatiles) return true;
			const item = (this.ignoringItem() ? '' : this.item);
			if (item === 'ironball') return true;
			if (!negateImmunity && this.hasType('Flying') && !(this.hasType('???') && 'roost' in this.volatiles)) return false;
			return null;
		};

		const runImmunity = Pokemon.prototype.runImmunity;
		Pokemon.prototype.runImmunity = function (type, message) {
			if (type !== 'Ground' || !message || !this.hasAbility('eelevate')) return runImmunity.call(this, type, message);
			if (runImmunity.call(this, type, false)) return true;
			const negateImmunity = !this.battle.runEvent('NegateImmunity', this, type);
			this.battle.add('-immune', this, ...(this.isGrounded(negateImmunity) === null ? ['[from] ability: Eelevate'] : []));
			return false;
		};
	}

	globalThis.PhantasmonNewMegas = {
		commit: DATA.commit,
		added,
		/** False once the engine lost what was added (Cobblemon resets its registries when a server resends them). */
		intact() {
			return added.species.every((id) => C.registries.species.has(id)) &&
				added.items.every((id) => C.registries.heldItem.has(id)) &&
				added.abilities.every((id) => C.registries.ability.has(id));
		},
	};
	return added;
})
