package dev.tee.stateditor.data;

import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.pokemon.Nature;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.util.Identifier;

/**
 * Re-implementation of Cobblemon's natural stat formula, so the UI can show the untouched value
 * next to the overridden one.
 * <p>
 * The formula mirrors {@code CobblemonStatProvider.getStatForPokemon} exactly (and therefore also
 * matches the formula Showdown uses inside a battle):
 * <pre>
 * non-HP: floor((2*base + iv + ev/4) * level / 100 + 5)  -> nature modifier
 * HP:     floor((2*base + iv + ev/4 + 100) * level / 100 + 10)
 * </pre>
 */
public final class StatMath {
    public static final Identifier SHEDINJA = Identifier.of("cobblemon", "shedinja");

    private StatMath() {
    }

    /** @return the stat value the Pokémon would have without any override. */
    public static int naturalStat(Pokemon pokemon, Stat stat) {
        Integer baseValue = pokemon.getForm().getBaseStats().get(stat);
        if (baseValue == null) {
            return 0;
        }
        int base = baseValue;
        int iv = pokemon.getIvs().getEffectiveBattleIV(stat);
        int ev = pokemon.getEvs().getOrDefault(stat);
        int level = pokemon.getLevel();

        if (stat == Stats.HP) {
            if (SHEDINJA.equals(pokemon.getSpecies().getResourceIdentifier())) {
                return 1;
            }
            return (int) Math.floor((Math.floor((2.0 * base + iv + (double) (ev / 4) + 100.0) * level / 100.0)) + 10.0);
        }
        int raw = (2 * base + iv + ev / 4) * level / 100 + 5;
        return pokemon.getEffectiveNature().modifyStat(stat, raw);
    }

    /**
     * @return the raw (pre-nature) stat value the Pokémon naturally has. This is the value
     * Showdown computes before it applies the nature modifier.
     */
    public static int naturalRaw(Pokemon pokemon, Stat stat) {
        int base = baseStat(pokemon, stat);
        int iv = pokemon.getIvs().getEffectiveBattleIV(stat);
        int ev = pokemon.getEvs().getOrDefault(stat);
        int level = pokemon.getLevel();
        if (stat == Stats.HP) {
            return (int) Math.floor(Math.floor((2.0 * base + iv + (double) (ev / 4) + 100.0) * level / 100.0) + 10.0);
        }
        return (2 * base + iv + ev / 4) * level / 100 + 5;
    }

    /**
     * @return the base stat of the Pokémon's current form, or {@code 0} when unknown.
     */
    public static int baseStat(Pokemon pokemon, Stat stat) {
        Integer baseValue = pokemon.getForm().getBaseStats().get(stat);
        return baseValue == null ? 0 : baseValue;
    }

    /**
     * @return {@code +1} when the nature boosts the stat, {@code -1} when it lowers it and {@code 0}
     * when the stat is unaffected.
     */
    public static int natureModifier(Pokemon pokemon, Stat stat) {
        return natureModifier(pokemon.getEffectiveNature(), stat);
    }

    public static int natureModifier(Nature nature, Stat stat) {
        if (nature == null) return 0;
        int increase = stat.equals(nature.getIncreasedStat()) ? 1 : 0;
        int decrease = stat.equals(nature.getDecreasedStat()) ? 1 : 0;
        return increase - decrease;
    }
}
