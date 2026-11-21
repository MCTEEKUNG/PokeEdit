package dev.tee.stateditor.data;

import java.util.function.Supplier;

/** Codec/copy setters expose temporary forms and IVs; never anchor legacy stats in that state. */
public final class StatHydration {
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private StatHydration() {
    }

    public static boolean active() {
        return DEPTH.get() > 0;
    }

    public static <T> T run(Supplier<T> operation) {
        int previous = DEPTH.get();
        DEPTH.set(previous + 1);
        try {
            return operation.get();
        } finally {
            if (previous == 0) DEPTH.remove();
            else DEPTH.set(previous);
        }
    }
}
