# PokéEdit reference

[Back to PokéEdit](../README.md)

## Commands

Party slots are numbered **1–6**.

| Command | Action |
| --- | --- |
| `/stat ui` | Open PokéEdit for your party. |
| `/stat ui <player>` | Open PokéEdit for another online player as an admin. |
| `/stat live` | Open the live stat editor for your party. |
| `/stat give <player>` | Give a player the PokéEdit Staff as an admin. |
| `/stat edit <slot> <stat> <value>` | Set one stat of your party member. |
| `/stat set <slot> <stat> <value> [<stat> <value> …]` | Set multiple stats for your party member. |
| `/stat edit other <player> <slot> <stat> <value>` | Set a stat for another online player’s Pokémon as an admin. |
| `/stat view <slot>` | Show current and natural stats for a party member. |
| `/stat reset <slot> [all or stat names]` | Clear overrides for all stats or the named stats. |

| Stat | Command names |
| --- | --- |
| HP | `hp`, `health` |
| Attack | `atk`, `attack` |
| Defense | `def`, `defence`, `defense` |
| Special Attack | `spa`, `special_attack` |
| Special Defense | `spd`, `special_defence`, `special_defense` |
| Speed | `spe`, `speed` |

## Party editor

- Clear the nickname to restore the species name. Changing species keeps the Pokémon’s identity and level.
- Enter `0` in an effective stat field to clear its bonus. IVs range from 0 to 31. EVs range from 0 to 252 per stat with a total limit of 510.
- Clear a move field to remove that move. A saved moveset needs at least one move, and each filled slot needs a different move.
- A draft applies to one slot. Save or reset before switching slots. The server accepts current drafts when both the editor and the party owner are out of battle.
- In a short window, use the arrows beside the Stats column headers to see the remaining rows. Scroll over a move, or use **Page Up** and **Page Down**, to read a description that exceeds the available space.
- Rebind **K** from Minecraft’s Controls menu. [Key bindings screenshot](key-bindings.png)

## Effective stats

The server validates edits and applies a signed bonus to the natural stat. Level, evolution, form, IVs, EVs, and nature changes continue to affect the natural stat. A reset removes the bonus. The default input range is 1–9999. Growth can take the effective stat past the input range, up to 2,147,483,647.

In the live editor, number fields save after a 300 ms pause. Leaving the field, switching Pokémon, or closing the editor also commits valid values. Use the step buttons to adjust a value or the reset button to restore a natural stat. [Live editor screenshot](stat-editor-gui.png)

The direct-stat bridge supports **Cobblemon’s built-in Graal Showdown service**. External socket Showdown servers require a matching bridge. The server rejects edited teams when the bridge is unavailable. Ordinary battle effects, such as boosts, held items, abilities, and Trick Room, still apply. Live changes preserve temporary typing, Transform state, and pending move choices. Lowering maximum HP clamps current HP. Raising it leaves current HP where it is.

## Permissions and settings

Players can edit their own Pokémon when `allowSelfEdit` is enabled. Editing another player’s party or giving the staff requires **operator level 2** or Cobblemon’s `pokemoneditother` permission.

The staff opens your party when you right-click air, a block, or a non-player entity. To select another party, aim at a visible player within the normal interaction range. The outline works in either hand. The staff is available from the Creative menu and the admin command.

Settings live in `config/stateditor.json`. In the client, Mod Menu also provides a settings screen.

| Setting | Default | Effect |
| --- | --- | --- |
| `minValue` | `1` | Smallest effective stat you can set. |
| `maxValue` | `9999` | Largest effective stat you can set. |
| `allowSelfEdit` | `true` | Allow players to edit their own party. |
| `removeItemFromNonCreative` | `true` | Remove the staff from players outside Creative without edit permission. |
| `showNaturalValues` | `true` | Show natural values alongside edited stats. |

## Upgrading

### From Party Manager

Stop Minecraft and the server. Replace Party Manager with PokéEdit on both sides. Changes saved to a player’s Cobblemon party stay in the party. Use the PokéEdit Staff in place of the Party Console. Keep a copy of your existing world data for rollback if you used per-trainer RCT or Cobblemon NPC overrides, which depended on Party Manager.

### From Stat Editor 1.0.0

Back up the world and update the client and server together. Version 1.1.0 introduced a new network channel and a signed bonus format. PokéEdit converts legacy absolute edits as Pokémon load, preserving the effective value at migration and allowing the stat to grow thereafter.

Existing `stateditor:pokeedit_console` items become the PokéEdit Staff. Items with ID `stateditor:stat_console` still open PokéEdit.

## Build and verify

Set `JAVA_HOME` or your `PATH` to use JDK 21, then run the Gradle wrapper.

```bash
./gradlew build
./gradlew test
node scripts/test-showdown.cjs /path/to/cobblemon-instance/showdown
```

The JUnit suite covers stat growth, bonus replacement, overflow, legacy conversion, preview fitting, and delayed input commits. The simulator check uses Cobblemon 1.7.3’s battle format to verify high stats, battle order, live edits and resets, growth, Transform, temporary typing, fixed-HP species, and request-state preservation. [Stat growth verification](stat-growth-verification.md)

### Implementation

- `StatOverrideStore` stores signed bonuses under `stateditor:bonuses` in each Pokémon’s persistent data.
- `PokemonGrowthMixin` anchors legacy `stateditor:overrides` at the previous level and form before growth. Loaded Pokémon migrate after hydration. `StatGrowth` applies bonuses after the natural formula.
- `StatGrowthSync` batches natural stat changes each server tick and refreshes live battles. Battle copies receive temporary recalculated bonuses.
- `CobblemonStatProviderMixin` applies overrides at Cobblemon’s stat provider. `BattleRegistryMixin` adds explicit stats to packed teams and preserves original fields, including IVs and EVs.
- `GraalShowdownServiceMixin` attaches the bridge when embedded Showdown starts or reloads. The bridge applies effective stats before battle modifiers. It uses wider arithmetic in battles with overrides.
