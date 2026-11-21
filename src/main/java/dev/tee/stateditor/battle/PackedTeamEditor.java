package dev.tee.stateditor.battle;

import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.tee.stateditor.StatEditorMod;

import java.util.List;

/**
 * Rewrites the packed team that Cobblemon hands over to Showdown so that overridden stats are
 * actually used for damage calculations.
 * <p>
 * The packed format produced by {@code BattleRegistry.packTeam} looks like this (segments are
 * separated by {@code |}, teams by {@code ]}):
 * <pre>
 * 0 showdownId | 1 (empty) | 2 uuid | 3 currentHealth | 4 status | 5 sleep | 6 item | 7 ability
 * 8 moves | 9 moveData | 10 nature | 11 evs | 12 gender | 13 ivs | 14 shiny | 15 level | 16 misc
 * </pre>
 */
public final class PackedTeamEditor {
    private static final int MIN_SEGMENTS = 16;

    private PackedTeamEditor() {
    }

    /**
     * Applies the stat overrides of every Pokémon in the team to the packed team string.
     *
     * @param packed the packed team produced by Cobblemon
     * @param team   the Pokémon the packed team was built from, in the same order
     * @return the rewritten packed team (or the original when nothing had to be changed)
     */
    public static String rewrite(String packed, List<? extends BattlePokemon> team) {
        if (packed == null || packed.isEmpty() || team == null || team.isEmpty()) {
            return packed;
        }
        String[] entries = packed.split("]", -1);
        if (entries.length != team.size()) {
            StatEditorMod.LOGGER.warn(
                    "Could not apply stat overrides: expected {} packed entries but found {}",
                    team.size(), entries.length);
            return packed;
        }
        StringBuilder result = new StringBuilder(packed.length());
        for (int i = 0; i < entries.length; i++) {
            if (i > 0) {
                result.append(']');
            }
            result.append(rewriteEntry(entries[i], team.get(i)));
        }
        return result.toString();
    }

    private static String rewriteEntry(String entry, BattlePokemon battlePokemon) {
        if (battlePokemon == null) {
            return entry;
        }
        Pokemon pokemon = battlePokemon.getEffectedPokemon();
        if (pokemon == null) {
            return entry;
        }
        String[] segments = entry.split("\\|", -1);
        if (segments.length < MIN_SEGMENTS) {
            StatEditorMod.LOGGER.warn("Unexpected packed team layout, skipping stat overrides");
            return entry;
        }
        // Also repair health saved by older versions after clearing a large HP override.
        try {
            segments[3] = String.valueOf(Math.min(Integer.parseInt(segments[3]), pokemon.getStat(Stats.HP)));
        } catch (NumberFormatException ignored) {
            // Leave Cobblemon's empty/default health field untouched.
        }
        int[] overrides = ShowdownOverrides.values(pokemon);
        for (int value : overrides) {
            if (value > 0) {
                ShowdownOverrides.requireSupportedService();
                break;
            }
        }
        return PackedStatCodec.append(String.join("|", segments), overrides);
    }
}
