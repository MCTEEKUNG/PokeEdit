package dev.tee.stateditor.battle;

/** An optional, versioned-by-prefix extension to Cobblemon's packed Pokémon. */
public final class PackedStatCodec {
    public static final String MARKER = "|stateditor:";

    private PackedStatCodec() {
    }

    /** Values are HP, Attack, Defence, Special Attack, Special Defence, Speed; 0 means natural. */
    public static String encode(int[] values) {
        if (values.length != 6) {
            throw new IllegalArgumentException("Expected six stats");
        }
        StringBuilder csv = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) csv.append(',');
            if (values[i] > 0) csv.append(values[i]);
        }
        return csv.toString();
    }

    public static String append(String entry, int[] values) {
        String csv = encode(values);
        if (csv.equals(",,,,,")) return entry;
        int existing = entry.lastIndexOf(MARKER);
        return (existing < 0 ? entry : entry.substring(0, existing)) + MARKER + csv;
    }
}
