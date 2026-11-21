package dev.tee.stateditor.data;

/** A permanent bonus, not a permanent final stat: natural growth continues underneath it. */
public final class StatGrowth {
    private StatGrowth() {
    }

    public static int bonusFor(int natural, int requested) {
        return Math.subtractExact(requested, natural);
    }

    public static int effective(int natural, int bonus) {
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, (long) natural + bonus));
    }
}
