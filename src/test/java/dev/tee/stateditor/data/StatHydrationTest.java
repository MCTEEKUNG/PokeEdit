package dev.tee.stateditor.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StatHydrationTest {
    @Test
    void nestedLoadingKeepsMigrationSuppressedUntilTheOuterOperationFinishes() {
        assertFalse(StatHydration.active());
        assertEquals(7, StatHydration.run(() -> {
            assertTrue(StatHydration.active());
            int result = StatHydration.run(() -> {
                assertTrue(StatHydration.active());
                return 7;
            });
            assertTrue(StatHydration.active());
            return result;
        }));
        assertFalse(StatHydration.active());
    }

    @Test
    void failedLoadingCannotDisableFutureMigration() {
        assertThrows(IllegalStateException.class, () -> StatHydration.run(() -> {
            throw new IllegalStateException("broken codec");
        }));
        assertFalse(StatHydration.active());
    }

    @Test
    void unrelatedThreadsAreNotConsideredLoading() throws InterruptedException {
        StatHydration.run(() -> {
            var active = new java.util.concurrent.atomic.AtomicBoolean(true);
            Thread thread = new Thread(() -> active.set(StatHydration.active()));
            thread.start();
            try { thread.join(); }
            catch (InterruptedException error) { throw new AssertionError(error); }
            assertFalse(active.get());
            return null;
        });
    }
}
