package dev.tee.stateditor.client;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PendingStatEditTest {
    private final UUID pokemon = UUID.randomUUID();

    @Test
    void typingSendsOnlyTheFinalValueAfterThePauseWithoutEnter() {
        PendingStatEdit edit = new PendingStatEdit();
        edit.update(pokemon, -1, "3", 1, 9999, 0);
        edit.update(pokemon, -1, "37", 1, 9999, 100);
        edit.update(pokemon, -1, "372", 1, 9999, 200);
        assertNull(edit.take(499, false));
        assertEquals(372, edit.take(500, false).value());
        assertNull(edit.take(1000, false), "do not send every tick");
    }

    @Test
    void losingFocusOrClosingFlushesToTheOriginalPokemon() {
        PendingStatEdit edit = new PendingStatEdit();
        edit.update(pokemon, 99, "5000", 1, 9999, 10);
        var pending = edit.take(11, true);
        assertEquals(pokemon, pending.pokemonId());
        assertEquals(99, pending.entityId());
        assertEquals(5000, pending.value());
    }

    @Test
    void emptyInvalidAndOutOfRangeDraftsCancelPreviousPendingValues() {
        for (String text : new String[]{"", "0", "10000", "abc", "999999999999999999999"}) {
            PendingStatEdit edit = new PendingStatEdit();
            edit.update(pokemon, -1, "100", 1, 9999, 0);
            assertFalse(edit.update(pokemon, -1, text, 1, 9999, 1));
            assertNull(edit.take(1000, true));
        }
    }

    @Test
    void resetCancelsTheDraftSoItCannotReappearLater() {
        PendingStatEdit edit = new PendingStatEdit();
        edit.update(pokemon, -1, "9999", 1, 9999, 0);
        edit.cancel();
        assertNull(edit.take(1000, true));
    }
}
