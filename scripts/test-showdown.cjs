// Run against the Showdown tree shipped with Cobblemon 1.7.3, not upstream Showdown.
// Usage: node scripts/test-showdown.cjs /path/to/showdown [--baseline]
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const {createRequire} = require('node:module');
const root = path.resolve(process.argv[2]);
const showdownRequire = createRequire(path.join(root, 'index.js'));
global.require = showdownRequire;
global.battleMap = new Map();
if (!process.argv.includes('--baseline')) {
    vm.runInThisContext(fs.readFileSync(path.join(__dirname,
        '../src/main/resources/data/stateditor/showdown/stat-overrides.js'), 'utf8'));
}
const {Battle} = showdownRequire('./sim/battle');
const {Teams} = showdownRequire('./sim/teams');
function options() {
    return {format: {id: 'stateditortest', name: 'Stat Editor Test', mod: 'cobblemon',
        gameType: 'singles', gen: 9, ruleset: []}, seed: [1, 2, 3, 4]};
}
function set(species, uuid) {
    return {species, name: species, uuid, moves: ['tackle'],
        movesInfo: [{pp: 35, maxpp: 35}], level: 5, nature: 'Modest',
        ivs: {hp: 0, atk: 0, def: 0, spa: 0, spd: 0, spe: 0},
        evs: {hp: 0, atk: 0, def: 0, spa: 0, spd: 0, spe: 0}};
}
function battle(species, overrides, level = 5) {
    const b = new Battle(options());
    const packed = [species, '', 'edited', '', '', '-1', '', '', 'tackle', '35/35',
        'Modest', '0,0,0,0,0,0', '', '0,0,0,0,0,0', '', String(level), '255'].join('|')
        + '|stateditor:' + overrides;
    b.setPlayer('p1', {name: 'Editor', team: packed});
    b.setPlayer('p2', {name: 'Control', team: [set('Blissey', 'control')]});
    global.battleMap.set('test', {battle: b});
    return b;
}
const b = battle('Magikarp', '9999,9999,8888,7777,6666,5555');
const p = b.p1.pokemon[0];
assert.equal(p.maxhp, 9999, 'HP must bypass the normal formula even at level 5');
assert.deepEqual(p.storedStats, {atk: 9999, def: 8888, spa: 7777, spd: 6666, spe: 5555});
assert.equal(p.baseStoredStats.atk, 9999);
assert.equal(p.getStat('atk', true, true), 9999, 'nature must not re-scale an explicit value');
assert.equal(p.speed, 5555);
stateditorUpdate('edited', '9999,9999,8888,7777,6666,9999');
assert.equal(p.getActionSpeed(), 9999, 'Speed must not wrap at 8192 in the actual Cobblemon format');
assert.equal(p.set.ivs.atk, 0, 'IVs must remain untouched');
assert.equal(p.set.evs.atk, 0, 'EVs must remain untouched');
const boostedDamage = b.actions.getDamage(p, b.p2.pokemon[0], 'tackle');
const control = new Battle(options());
control.setPlayer('p1', {name: 'A', team: [set('Magikarp', 'plain')]});
control.setPlayer('p2', {name: 'B', team: [set('Blissey', 'target')]});
const normalDamage = control.actions.getDamage(control.p1.pokemon[0], control.p2.pokemon[0], 'tackle');
assert.ok(boostedDamage > normalDamage * 10, `${boostedDamage} vs ${normalDamage}: battle damage must actually change`);
p.hp = 5000;
p.setType(['Fire']);
p.addType('Ghost');
let extraRequests = 0;
const emitRequest = b.p1.emitRequest;
b.p1.emitRequest = function (...args) { extraRequests++; return emitRequest.apply(this, args); };
stateditorUpdate('edited', '1234,4321,,,,');
assert.equal(extraRequests, 0, 'live editing must not re-emit requests and erase Cobblemon pending choices');
assert.deepEqual(p.types, ['Fire'], 'live edits must not reset temporary typing');
assert.equal(p.addedType, 'Ghost');
assert.equal(p.maxhp, 1234);
assert.equal(p.hp, 1234, 'reducing max HP clamps current HP');
assert.equal(p.storedStats.atk, 4321, 'edits update an existing battle');
assert.ok(b.p1.activeRequest.side.pokemon[0].condition.startsWith('1234/1234'),
    'battle request data must be refreshed after a live HP edit');
stateditorUpdate('edited', ',,,,,');
assert.equal(p.storedStats.atk, control.p1.pokemon[0].storedStats.atk, 'reset restores natural stats');
assert.equal(p.maxhp, control.p1.pokemon[0].maxhp);
const shedinja = battle('Shedinja', '5000,9999,,,,').p1.pokemon[0];
assert.equal(shedinja.maxhp, 5000, 'even fixed-HP species must accept overrides');
assert.equal(shedinja.storedStats.atk, 9999);
const transformed = new Battle(options());
transformed.setPlayer('p1', {name: 'Ditto', team: [set('Ditto', 'ditto')]});
transformed.setPlayer('p2', {name: 'Blissey', team: [{...set('Blissey', 'blissey'), stateditorStats: {atk: 5000}}]});
global.battleMap.set('transform', {battle: transformed});
const ditto = transformed.p1.pokemon[0];
assert.ok(ditto.transformInto(transformed.p2.pokemon[0]));
const dittoHp = ditto.maxhp;
const copiedAtk = ditto.storedStats.atk;
stateditorUpdate('ditto', ',,,,9000,');
assert.equal(ditto.maxhp, dittoHp, 'unrelated edits must not replace transformed HP with the copied species HP');
assert.equal(ditto.storedStats.atk, copiedAtk, 'unrelated edits must preserve Transform copied stats');
stateditorUpdate('ditto', ',,,,,');
assert.equal(ditto.storedStats.atk, copiedAtk);
stateditorUpdate('ditto', ',6000,,,,', true);
assert.equal(ditto.storedStats.atk, copiedAtk, 'natural growth must preserve Transform copied combat stats');
assert.equal(ditto.set.stateditorStats.atk, 6000, 'growth still updates the underlying edited stat');
ditto.clearVolatile();
assert.equal(ditto.storedStats.atk, 6000, 'grown stats take effect when Transform ends');
// Reproduce cartridge overflow regressions: damage must grow, not wrap at 16/32 bits.
function damageAt(atk) {
    const test = new Battle(options());
    test.setPlayer('p1', {name: 'A', team: [{...set('Magikarp', 'a'), level: 100, stateditorStats: {atk}}]});
    test.setPlayer('p2', {name: 'B', team: [{...set('Blissey', 'b'), level: 100, stateditorStats: {def: 1}}]});
    return test.actions.getDamage(test.p1.pokemon[0], test.p2.pokemon[0], 'tackle');
}
assert.ok(damageAt(2500) > damageAt(1950), 'high Attack must not overflow into lower damage');
assert.ok(damageAt(9999) > damageAt(2500));
function firstMover(trickRoom) {
    const test = new Battle(options());
    const common = {hp: 9999, atk: 1, def: 9999};
    test.setPlayer('p1', {name: 'Fast', team: [{...set('Magikarp', 'fast'), stateditorStats: {...common, spe: 9999}}]});
    test.setPlayer('p2', {name: 'Slow', team: [{...set('Magikarp', 'slow'), stateditorStats: {...common, spe: 8000}}]});
    if (trickRoom) test.field.addPseudoWeather('trickroom', test.p1.pokemon[0]);
    test.choose('p1', 'move 1');
    test.choose('p2', 'move 1');
    return test.log.find(line => line.startsWith('|move|'));
}
assert.ok(firstMover(false).startsWith('|move|p1a:'), '9999 Speed must beat 8000 in an actual turn');
assert.ok(firstMover(true).startsWith('|move|p2a:'), 'Trick Room still reverses Speed normally');
const pending = new Battle(options());
pending.setPlayer('p1', {name: 'A', team: [{...set('Magikarp', 'pending'), stateditorStats: {hp: 9999}}]});
pending.setPlayer('p2', {name: 'B', team: [set('Magikarp', 'waiting')]});
global.battleMap.set('pending', {battle: pending});
pending.choose('p1', 'move 1');
const chosenAction = pending.p1.choice.actions[0];
const healthUpdates = JSON.parse(stateditorUpdate('pending', '8000,,,,,'));
assert.equal(healthUpdates[0].maxhp, 8000, 'Java bridge receives exact updated maximum HP');
assert.equal(pending.p1.choice.actions[0], chosenAction, 'a submitted move must survive a live stat edit');
pending.choose('p2', 'move 1');
assert.equal(pending.turn, 2, 'battle must continue after an edit between player choices');
const plainEntry = 'Magikarp||plain|||-1|||tackle|35/35|Modest|0,0,0,0,0,0||0,0,0,0,0,0||5|255';
const mixed = Teams.unpack(plainEntry + ']' + plainEntry.replace('plain', 'edited2') + '|stateditor:,9000,,,,');
assert.equal(mixed.length, 2);
assert.equal(mixed[0].stateditorStats, undefined);
assert.deepEqual(mixed[1].stateditorStats, {atk: 9000});
// Java supplies current natural stats + stored bonuses at each battle entry, not fixed edits.
const natural5 = battle('Bulbasaur', ',,,,,', 5).p1.pokemon[0];
const natural6 = battle('Bulbasaur', ',,,,,', 6).p1.pokemon[0];
const statKeys = ['hp', 'atk', 'def', 'spa', 'spd', 'spe'];
const naturalValue = (pokemon, key) => key === 'hp' ? pokemon.maxhp : pokemon.storedStats[key];
const edited5 = statKeys.map((key, i) => i === 0 ? 100 : 9999);
const edited6 = statKeys.map((key, i) => edited5[i] + naturalValue(natural6, key) - naturalValue(natural5, key));
const grown = battle('Bulbasaur', edited6.join(','), 6).p1.pokemon[0];
for (let i = 0; i < statKeys.length; i++) {
    assert.equal(naturalValue(grown, statKeys[i]), edited6[i], `grown ${statKeys[i]} reaches simulator unchanged`);
}
assert.ok(grown.maxhp > 100, 'edited HP grows from level 5 to 6');
assert.ok(grown.storedStats.spe > 9999, 'growth is not clamped to the editor input maximum');
const liveGrown = battle('Bulbasaur', edited5.join(','), 5).p1.pokemon[0];
const grownNaturalCsv = statKeys.map(key => naturalValue(natural6, key)).join(',');
stateditorUpdate('edited', edited6.join(','), true, grownNaturalCsv);
stateditorUpdate('edited', ',,,,,', false, grownNaturalCsv);
for (const key of statKeys) {
    assert.equal(naturalValue(liveGrown, key), naturalValue(natural6, key), `reset after growth uses current natural ${key}`);
}
const natural7 = battle('Bulbasaur', ',,,,,', 7).p1.pokemon[0];
global.battleMap.set('test', {battle: liveGrown.battle});
stateditorUpdate('edited', ',,,,,', false, statKeys.map(key => naturalValue(natural7, key)).join(','), 'bulbasaur');
for (const key of statKeys) {
    assert.equal(naturalValue(liveGrown, key), naturalValue(natural7, key), `already-reset ${key} still grows`);
}
const baseCharizard = battle('Charizard', ',,,,,').p1.pokemon[0];
const battleForm = battle('Charizard', ',5000,,,,').p1.pokemon[0];
assert.ok(battleForm.formeChange('Charizard-Mega-X', null, true));
const megaNaturalAttack = battleForm.stateditorUnderlyingStats.atk;
stateditorUpdate('edited', ',,,,,', false, statKeys.map(key => naturalValue(baseCharizard, key)).join(','), 'charizard');
assert.equal(battleForm.storedStats.atk, megaNaturalAttack, 'reset preserves simulator-only form natural stats');
// Installing twice must not wrap the same methods again (e.g. a Showdown reload).
const installed = Battle.prototype.spreadModify;
vm.runInThisContext(fs.readFileSync(path.join(__dirname,
    '../src/main/resources/data/stateditor/showdown/stat-overrides.js'), 'utf8'));
assert.equal(Battle.prototype.spreadModify, installed);
assert.equal(Teams.unpack('Magikarp||untouched|||-1|||tackle|35/35|Modest|0,0,0,0,0,0||0,0,0,0,0,0||5|255')[0].stateditorStats, undefined);
console.log(`PASS: direct stats, real damage (${boostedDamage} vs ${normalDamage}), live edit/reset, level growth, fixed-HP species, untouched IV/EV`);
