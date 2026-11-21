package dev.tee.stateditor.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StatGrowthTest {
    @Test
    void editedHpContinuesGrowingWithTheNaturalStat() {
        int bonus = StatGrowth.bonusFor(20, 100);
        assertEquals(80, bonus);
        assertEquals(100, StatGrowth.effective(20, bonus));
        assertEquals(102, StatGrowth.effective(22, bonus));
        assertEquals(180, StatGrowth.effective(100, bonus));
    }

    @Test
    void editingAgainReplacesTheBonusWithoutCompounding() {
        int replacement = StatGrowth.bonusFor(22, 150);
        assertEquals(150, StatGrowth.effective(22, replacement));
        assertEquals(153, StatGrowth.effective(25, replacement));
    }

    @Test
    void negativeBonusesGrowTooAndStayPositiveWhenLevelDrops() {
        int bonus = StatGrowth.bonusFor(50, 10);
        assertEquals(12, StatGrowth.effective(52, bonus));
        assertEquals(1, StatGrowth.effective(5, bonus));
        assertEquals(10, StatGrowth.effective(50, bonus));
    }

    @Test
    void naturalGrowthIsNotClampedByTheEditorInputMaximum() {
        int bonus = StatGrowth.bonusFor(20, 9999);
        assertEquals(10001, StatGrowth.effective(22, bonus));
    }

    @Test
    void largeValuesNeverWrapNegative() {
        int bonus = StatGrowth.bonusFor(1, Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, StatGrowth.effective(500, bonus));
        assertEquals(1, StatGrowth.effective(1, Integer.MIN_VALUE));
    }

    @Test
    void legacyAbsoluteValuesKeepTheirCurrentValueWhenConverted() {
        int migrated = StatGrowth.bonusFor(37, 100);
        assertEquals(100, StatGrowth.effective(37, migrated));
        assertEquals(103, StatGrowth.effective(40, migrated));
    }
}
