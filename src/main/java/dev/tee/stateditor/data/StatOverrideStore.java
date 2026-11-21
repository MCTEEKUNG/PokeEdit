package dev.tee.stateditor.data;

import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stores additive bonuses so edited stats keep growing with level, form, IVs and EVs.
 * <p>
 * The overrides live inside {@link Pokemon#getPersistentData()}, which Cobblemon stores as part of
 * the Pokémon codec ({@code PokemonP2}). That means they are written to disk with the party/PC data
 * and are transferred to clients whenever the Pokémon itself is transferred - no extra bookkeeping
 * required.
 */
public final class StatOverrideStore {
    /** Legacy absolute values, converted lazily without changing the current displayed stat. */
    public static final String NBT_KEY = "stateditor:overrides";
    public static final String BONUS_NBT_KEY = "stateditor:bonuses";

    private StatOverrideStore() {
    }

    /**
     * @return the persistent data of a Pokémon. Cobblemon can leave it uninitialised, in that case
     * an empty compound is created and attached to the Pokémon.
     */
    private static NbtCompound persistentData(Pokemon pokemon, boolean create) {
        NbtCompound data = pokemon.getPersistentData();
        if (data == null) {
            if (!create) {
                return null;
            }
            data = new NbtCompound();
            pokemon.setPersistentData$common(data);
        }
        return data;
    }

    /** @return the bonus compound of a Pokémon. Never {@code null}. */
    public static NbtCompound overrides(Pokemon pokemon) {
        migrateLegacy(pokemon);
        NbtCompound data = persistentData(pokemon, false);
        if (data == null || !data.contains(BONUS_NBT_KEY, NbtElement.COMPOUND_TYPE)) {
            return new NbtCompound();
        }
        return data.getCompound(BONUS_NBT_KEY);
    }

    /** @return the overridden value of a stat, or {@code null} when the stat is untouched. */
    public static Integer get(Pokemon pokemon, Stat stat) {
        Integer bonus = getBonus(pokemon, stat);
        if (bonus != null) return StatGrowth.effective(StatMath.naturalStat(pokemon, stat), bonus);
        // Preserve old absolute reads during hydration without consuming/migrating their NBT.
        if (StatHydration.active()) {
            NbtCompound data = persistentData(pokemon, false);
            if (data != null && data.contains(NBT_KEY, NbtElement.COMPOUND_TYPE)) {
                NbtCompound legacy = data.getCompound(NBT_KEY);
                if (legacy.contains(StatIds.key(stat), NbtElement.INT_TYPE)) {
                    return Math.max(1, legacy.getInt(StatIds.key(stat)));
                }
            }
        }
        return null;
    }

    /** Raw signed bonus for persistence and server-authoritative synchronization. */
    public static Integer getBonus(Pokemon pokemon, Stat stat) {
        migrateLegacy(pokemon);
        NbtCompound data = persistentData(pokemon, false);
        if (data == null || !data.contains(BONUS_NBT_KEY, NbtElement.COMPOUND_TYPE)) {
            return null;
        }
        NbtCompound tag = data.getCompound(BONUS_NBT_KEY);
        String key = StatIds.key(stat);
        if (!tag.contains(key, NbtElement.INT_TYPE)) {
            return null;
        }
        return tag.getInt(key);
    }

    /** @return {@code true} when the stat has been overridden. */
    public static boolean isOverridden(Pokemon pokemon, Stat stat) {
        return get(pokemon, stat) != null;
    }

    /** Sets (or replaces) the override of a stat. */
    public static void set(Pokemon pokemon, Stat stat, int value) {
        setBonus(pokemon, stat, StatGrowth.bonusFor(StatMath.naturalStat(pokemon, stat), value));
    }

    /** Applies the server's exact bonus; never derive it from a client's potentially stale level. */
    public static void setBonus(Pokemon pokemon, Stat stat, int bonus) {
        migrateLegacy(pokemon);
        NbtCompound data = persistentData(pokemon, true);
        NbtCompound tag = new NbtCompound();
        if (data.contains(BONUS_NBT_KEY, NbtElement.COMPOUND_TYPE)) {
            tag = data.getCompound(BONUS_NBT_KEY);
        }
        tag.putInt(StatIds.key(stat), bonus);
        data.put(BONUS_NBT_KEY, tag);
        markDirty(pokemon);
    }

    /** Removes the override of a single stat, restoring the naturally computed value. */
    public static void clear(Pokemon pokemon, Stat stat) {
        NbtCompound data = persistentData(pokemon, false);
        if (data == null) return;
        boolean changed = false;
        for (String root : new String[]{BONUS_NBT_KEY, NBT_KEY}) {
            if (!data.contains(root, NbtElement.COMPOUND_TYPE)) continue;
            NbtCompound tag = data.getCompound(root);
            if (!tag.contains(StatIds.key(stat))) continue;
            tag.remove(StatIds.key(stat));
            if (tag.isEmpty()) data.remove(root);
            changed = true;
        }
        if (changed) markDirty(pokemon);
    }

    /** Removes every override of a Pokémon. */
    public static void clearAll(Pokemon pokemon) {
        NbtCompound data = persistentData(pokemon, false);
        if (data != null) {
            data.remove(NBT_KEY);
            data.remove(BONUS_NBT_KEY);
            markDirty(pokemon);
        }
    }

    /** @return every override of a Pokémon, keyed by stat. */
    public static Map<Stat, Integer> all(Pokemon pokemon) {
        Map<Stat, Integer> result = new LinkedHashMap<>();
        for (Stat stat : StatIds.EDITABLE) {
            Integer value = get(pokemon, stat);
            if (value != null) result.put(stat, value);
        }
        return result;
    }

    /** Converts old absolute edits once, at the existing level, before any level/form mutation. */
    public static void migrateLegacy(Pokemon pokemon) {
        if (StatHydration.active()) return;
        NbtCompound data = persistentData(pokemon, false);
        if (data == null || !data.contains(NBT_KEY, NbtElement.COMPOUND_TYPE)) return;
        NbtCompound legacy = data.getCompound(NBT_KEY).copy();
        NbtCompound bonuses = data.getCompound(BONUS_NBT_KEY).copy();
        boolean changed = false;
        for (Stat stat : StatIds.EDITABLE) {
            String key = StatIds.key(stat);
            if (!legacy.contains(key, NbtElement.INT_TYPE)) continue;
            if (!bonuses.contains(key, NbtElement.INT_TYPE)) {
                bonuses.putInt(key, StatGrowth.bonusFor(StatMath.naturalStat(pokemon, stat),
                        Math.max(1, legacy.getInt(key))));
            }
            legacy.remove(key);
            changed = true;
        }
        if (!changed) return;
        data.put(BONUS_NBT_KEY, bonuses);
        if (legacy.isEmpty()) data.remove(NBT_KEY);
        else data.put(NBT_KEY, legacy);
        markDirty(pokemon);
    }

    private static void markDirty(Pokemon pokemon) {
        if (!pokemon.isClient$common()) pokemon.onChange(null);
    }
}
