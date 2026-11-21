package dev.tee.stateditor.client;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;

/** Keeps labels legible over the blurred world with a backing only as wide as the text. */
final class ReadableLabel {
    private ReadableLabel() {
    }

    static void draw(DrawContext context, TextRenderer renderer, OrderedText text, int x, int y, int color) {
        int width = renderer.getWidth(text);
        if (width == 0) {
            return;
        }
        context.fill(x - 2, y - 1, x + width + 2, y + renderer.fontHeight + 1, 0x990A1018);
        context.drawTextWithShadow(renderer, text, x, y, color);
    }
}
