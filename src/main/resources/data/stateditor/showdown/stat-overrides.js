// Loaded into Cobblemon's existing Graal context after its own index.js.
// No files in Cobblemon's Showdown installation are modified.
(() => {
    const {Teams} = require('./sim/teams');
    const {Battle} = require('./sim/battle');
    const {Pokemon} = require('./sim/pokemon');
    if (Battle.prototype.stateditorInstalled) return;
    const keys = ['hp', 'atk', 'def', 'spa', 'spd', 'spe'];
    const marker = '|stateditor:';
    function enableWideArithmetic(battle) {
        if (battle.stateditorWideArithmetic) return;
        // Showdown normally reproduces cartridge integer overflow (13-bit Speed,
        // 16-bit final damage, 32-bit intermediates). Edited battles must not wrap.
        battle.trunc = Math.trunc;
        // A per-battle copy also disables the cartridge 10000 Speed cap. Never mutate Dex formats.
        battle.format = Object.assign(Object.create(Object.getPrototypeOf(battle.format)), battle.format,
            {battle: {...battle.format.battle, trunc: Math.trunc}});
        battle.stateditorWideArithmetic = true;
    }
    function decode(csv) {
        const values = csv.split(',');
        const stats = {};
        keys.forEach((key, i) => {
            const value = Number(values[i]);
            if (Number.isSafeInteger(value) && value > 0 && value <= 2147483647) stats[key] = value;
        });
        return stats;
    }
    const unpack = Teams.unpack;
    Teams.unpack = function (packed) {
        if (typeof packed !== 'string' || !packed.includes(marker)) return unpack.call(this, packed);
        const overrides = [];
        const clean = packed.split(']').map(entry => {
            const index = entry.lastIndexOf(marker);
            overrides.push(index < 0 ? undefined : decode(entry.slice(index + marker.length)));
            return index < 0 ? entry : entry.slice(0, index);
        }).join(']');
        const team = unpack.call(this, clean);
        if (team) team.forEach((set, i) => {
            if (overrides[i]) set.stateditorStats = overrides[i];
        });
        return team;
    };
    const spreadModify = Battle.prototype.spreadModify;
    Battle.prototype.spreadModify = function (baseStats, set) {
        if (set.stateditorStats && Object.keys(set.stateditorStats).length) enableWideArithmetic(this);
        const stats = spreadModify.call(this, baseStats, set);
        set.stateditorNaturalStats = {...stats};
        // Apply after IVs, EVs, level and nature, before battle boosts/abilities/items.
        return Object.assign(stats, set.stateditorStats || {});
    };
    const setSpecies = Pokemon.prototype.setSpecies;
    Pokemon.prototype.setSpecies = function (...args) {
        const initializing = !this.maxhp;
        const result = setSpecies.apply(this, args);
        const hp = this.set.stateditorStats?.hp;
        if (result && !args[2]) this.stateditorUnderlyingStats = {...this.set.stateditorNaturalStats};
        if (result && initializing) {
            this.stateditorNaturalHp = this.species.maxHP || this.set.stateditorNaturalStats.hp;
        }
        // Species with fixed maxHP (e.g. Shedinja) bypass spreadModify's HP result.
        if (result && initializing && hp) this.hp = this.baseMaxhp = this.maxhp = hp;
        return result;
    };
    const transformInto = Pokemon.prototype.transformInto;
    Pokemon.prototype.transformInto = function (...args) {
        const result = transformInto.apply(this, args);
        if (result) this.stateditorUnderlyingStats = {...this.storedStats};
        return result;
    };
    globalThis.stateditorUpdate = (uuid, csv, naturalGrowth = false, naturalCsv = '', sourceSpecies = '') => {
        const overrides = decode(csv);
        const natural = decode(naturalCsv);
        const updates = [];
        for (const [battleId, stream] of battleMap) {
            const battle = stream.battle;
            if (!battle || battle.ended) continue;
            let updated = false;
            for (const side of battle.sides) {
                if (!side) continue;
                for (const pokemon of side.pokemon) {
                    if (pokemon.uuid !== uuid) continue;
                    const previous = pokemon.set.stateditorStats || {};
                    const underlying = pokemon.stateditorUnderlyingStats || {};
                    const sourceId = sourceSpecies ? battle.dex.species.get(sourceSpecies).id : '';
                    // Mega evolution can replace baseSpecies too, so compare with the actual source form.
                    const battleForm = pokemon.species.id !== (sourceId || pokemon.baseSpecies.id);
                    const naturalChanged = new Set();
                    // Resets after growth must use today's natural stats, not the battle-entry level.
                    // A simulator-only form still owns its own natural baseline; Transform keeps original HP.
                    if (natural.hp && (!battleForm || pokemon.transformed)) {
                        if (pokemon.stateditorNaturalHp !== natural.hp) naturalChanged.add('hp');
                        pokemon.stateditorNaturalHp = natural.hp;
                    }
                    if (!pokemon.transformed && !battleForm) {
                        for (const key of keys) {
                            if (key === 'hp' || !natural[key]) continue;
                            if (underlying[key] !== natural[key]) naturalChanged.add(key);
                            underlying[key] = natural[key];
                        }
                    }
                    pokemon.stateditorUnderlyingStats = underlying;
                    const changed = keys.filter(key => previous[key] !== overrides[key] ||
                        (overrides[key] === undefined && naturalChanged.has(key)));
                    if (!changed.length) continue;
                    if (Object.keys(overrides).length) enableWideArithmetic(battle);
                    for (const key of changed) {
                        if (key === 'hp') {
                            const hpMultiplier = pokemon.baseMaxhp ? pokemon.maxhp / pokemon.baseMaxhp : 1;
                            pokemon.baseMaxhp = overrides.hp || pokemon.stateditorNaturalHp;
                            pokemon.maxhp = Math.floor(pokemon.baseMaxhp * hpMultiplier);
                            pokemon.hp = Math.min(pokemon.hp, pokemon.maxhp);
                            if (pokemon.isActive) battle.add('-sethp', pokemon, pokemon.getHealth);
                        } else if (!naturalGrowth || !pokemon.transformed) {
                            if (previous[key] === undefined && (pokemon.transformed || !natural[key])) {
                                underlying[key] = pokemon.storedStats[key];
                            }
                            const value = overrides[key] || underlying[key];
                            pokemon.storedStats[key] = value;
                            if (!pokemon.transformed) pokemon.baseStoredStats[key] = value;
                            if (pokemon.modifiedStats) pokemon.modifiedStats[key] = value;
                        }
                    }
                    pokemon.stateditorUnderlyingStats = underlying;
                    pokemon.set.stateditorStats = overrides;
                    pokemon.updateSpeed();
                    updated = true;
                    // Do not re-emit |request|: Cobblemon clears its pending Java-side choices on it.
                    // Health reaches the battle HUD through an explicit max-health packet instead.
                    if (side.activeRequest) side.activeRequest = {...side.activeRequest, side: side.getRequestData()};
                    updates.push({battleId, actorId: side.id, hp: pokemon.hp, maxhp: pokemon.maxhp,
                        hpChanged: changed.includes('hp'), active: pokemon.isActive});
                }
            }
            if (updated) battle.sendUpdates();
        }
        return JSON.stringify(updates);
    };
    Battle.prototype.stateditorInstalled = true;
})();
