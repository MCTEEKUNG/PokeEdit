package dev.tee.stateditor.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PreviewAreaTest {
    @Test
    void emptyMessageIsCenteredInTheModelAreaNotTheStatPanel() {
        var preview = new PreviewArea(8, 39, 136, 88);
        assertEquals(16, preview.centeredX(120));
        assertEquals(78, preview.centeredY(9));
        assertTrue(preview.centeredX(120) + 120 < 8 + 136 + 12);
    }

    @Test
    void wrappedMessagesAreCenteredAsOneBlock() {
        var preview = new PreviewArea(25, 39, 136, 88);
        assertEquals(29, preview.centeredX(128));
        assertEquals(73, preview.centeredY(20));
        assertEquals(39 + 88 / 2, preview.centeredY(20) + 20 / 2);
    }
}
