package dev.tee.stateditor.client;

import java.util.UUID;

/** Debounces typing while retaining the original target across focus/party changes. */
final class PendingStatEdit {
    static final long DELAY_MS = 300;
    private Edit edit;
    private long changedAt;

    boolean update(UUID pokemonId, int entityId, String text, int min, int max, long now) {
        cancel();
        try {
            int value = Integer.parseInt(text.trim());
            if (value < min || value > max) return false;
            edit = new Edit(pokemonId, entityId, value);
            changedAt = now;
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    Edit take(long now, boolean force) {
        if (edit == null || (!force && now - changedAt < DELAY_MS)) return null;
        Edit result = edit;
        cancel();
        return result;
    }

    void cancel() {
        edit = null;
    }

    record Edit(UUID pokemonId, int entityId, int value) {
    }
}
