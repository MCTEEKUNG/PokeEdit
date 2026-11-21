package dev.tee.stateditor.battle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PackedStatCodecTest {
    @Test
    void preservesAllOriginalFieldsAndHighValues() {
        String original = "magikarp||uuid|17||-1|||tackle|35/35|Modest|0,0,0,0,0,0||0,0,0,0,0,0||5|255";
        assertEquals(original + "|stateditor:9999,9999,,1,,2147483647",
                PackedStatCodec.append(original, new int[]{9999, 9999, 0, 1, 0, Integer.MAX_VALUE}));
    }

    @Test
    void leavesUneditedPokemonAlone() {
        assertEquals("original", PackedStatCodec.append("original", new int[6]));
    }

    @Test
    void replacementDoesNotDuplicateTheExtension() {
        assertEquals("original|stateditor:,5000,,,,", PackedStatCodec.append(
                "original|stateditor:100,,,,,", new int[]{0, 5000, 0, 0, 0, 0}));
    }

    @Test
    void rejectsWrongStatCount() {
        assertThrows(IllegalArgumentException.class, () -> PackedStatCodec.encode(new int[5]));
    }
}
