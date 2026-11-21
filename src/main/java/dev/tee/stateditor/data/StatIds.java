package dev.tee.stateditor.data;

import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Translation layer between user facing stat names and Cobblemon's {@link Stat} interface.
 * <p>
 * Cobblemon ships {@link Stats} with eight entries, but only the six "permanent" ones have a real
 * battle value. Those six are the ones this mod exposes.
 */
public final class StatIds {
    /** The stats a user is allowed to edit, in the order Cobblemon uses for EVs/IVs. */
    public static final List<Stat> EDITABLE = List.of(
            Stats.HP,
            Stats.ATTACK,
            Stats.DEFENCE,
            Stats.SPECIAL_ATTACK,
            Stats.SPECIAL_DEFENCE,
            Stats.SPEED
    );

    private static final Map<String, Stat> ALIASES = new LinkedHashMap<>();
    private static final Map<Stat, String> SHORT_NAMES = new LinkedHashMap<>();

    static {
        alias("hp", Stats.HP);
        alias("health", Stats.HP);
        alias("atk", Stats.ATTACK);
        alias("attack", Stats.ATTACK);
        alias("def", Stats.DEFENCE);
        alias("defence", Stats.DEFENCE);
        alias("defense", Stats.DEFENCE);
        alias("spa", Stats.SPECIAL_ATTACK);
        alias("special_attack", Stats.SPECIAL_ATTACK);
        alias("spatk", Stats.SPECIAL_ATTACK);
        alias("sp_atk", Stats.SPECIAL_ATTACK);
        alias("spd", Stats.SPECIAL_DEFENCE);
        alias("special_defence", Stats.SPECIAL_DEFENCE);
        alias("special_defense", Stats.SPECIAL_DEFENCE);
        alias("spdef", Stats.SPECIAL_DEFENCE);
        alias("sp_def", Stats.SPECIAL_DEFENCE);
        alias("spe", Stats.SPEED);
        alias("speed", Stats.SPEED);

        SHORT_NAMES.put(Stats.HP, "hp");
        SHORT_NAMES.put(Stats.ATTACK, "atk");
        SHORT_NAMES.put(Stats.DEFENCE, "def");
        SHORT_NAMES.put(Stats.SPECIAL_ATTACK, "spa");
        SHORT_NAMES.put(Stats.SPECIAL_DEFENCE, "spd");
        SHORT_NAMES.put(Stats.SPEED, "spe");
    }

    private StatIds() {
    }

    private static void alias(String name, Stat stat) {
        ALIASES.put(name, stat);
    }

    /**
     * Strictly parses a user supplied stat name.
     *
     * @return the matching stat, or {@code null} when the name is not a known stat.
     */
    public static Stat parse(String raw) {
        if (raw == null) {
            return null;
        }
        String key = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return ALIASES.get(key);
    }

    /** @return the stable storage key of a stat, e.g. {@code cobblemon:attack}. */
    public static String key(Stat stat) {
        return stat.getIdentifier().toString();
    }

    /** @return the stable storage key of a stat, or {@code null} when unknown. */
    public static Stat fromKey(String key) {
        if (key == null) {
            return null;
        }
        for (Stat stat : EDITABLE) {
            if (key(stat).equals(key)) {
                return stat;
            }
        }
        return null;
    }

    /** @return the short command friendly name of a stat, e.g. {@code atk}. */
    public static String shortName(Stat stat) {
        String shortName = SHORT_NAMES.get(stat);
        return shortName != null ? shortName : stat.getIdentifier().getPath();
    }

    /** @return the localised display name of a stat. */
    public static Text displayName(Stat stat) {
        return stat.getDisplayName();
    }

    /** @return every accepted alias, for tab completion. */
    public static List<String> aliases() {
        return new ArrayList<>(ALIASES.keySet());
    }
}
