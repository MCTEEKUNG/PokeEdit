package dev.tee.stateditor.client;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.client.gui.TypeIcon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Measured when a move or window changes, in Minecraft's scaled GUI coordinates. */
final class MoveDetailsCard {
    static final Text BROWSE_HINT = Text.translatable("stateditor.move.browse");
    private static final int MARGIN = 4;
    private static final int PAD = 6;
    private static final int GAP = 3;
    private static final int BODY = 0xFF211D29;
    private static final int LINE = 0xFF61566D;
    private static final int INK = 0xFFFAF7FF;
    private static final int MUTED = 0xFFD1C7DB;

    private final TextRenderer font;
    private final int width;
    private final int height;
    private final int accent;
    private final TypeIcon icon;
    private final List<OrderedText> title;
    private final List<OrderedText> classification;
    private final List<OrderedText> effect;
    private record Metric(int width, List<OrderedText> label, List<OrderedText> value) {}
    private final List<Metric> metrics = new ArrayList<>(3);
    private final List<OrderedText> footer;
    private final int headerHeight;
    private final int metricHeight;
    private final int visibleLines;
    private final Text narration;
    private int firstLine;

    MoveDetailsCard(MoveTemplate move, Pokemon pokemon, TextRenderer font, int screenWidth, int screenHeight) {
        this.font = font;
        width = Math.min(screenWidth - MARGIN * 2, screenHeight < 160 ? 256 : 218);
        var type = move.getElementalType();
        var category = move.getDamageCategory();
        accent = 0xFF000000 | type.getHue();
        var name = move.getDisplayName().formatted(Formatting.BOLD);
        var subtitle = Text.empty().append(type.getDisplayName()).append(" · ").append(category.getDisplayName());
        title = font.wrapLines(name, width - 4 * PAD);
        classification = font.wrapLines(subtitle, width - 4 * PAD - 12);
        int subtitleY = 2 * PAD + title.size() * font.fontHeight;
        icon = new TypeIcon(3 * PAD, subtitleY, type, null, false, true, 0f, 0f, 1f);
        headerHeight = subtitleY + classification.size() * font.fontHeight;

        Text powerLabel = switch (category.getName().toLowerCase(Locale.ROOT)) {
            case "special" -> Text.translatable("stateditor.move.special_power");
            case "physical" -> Text.translatable("stateditor.move.physical_power");
            default -> Text.translatable("cobblemon.ui.power");
        };
        Text power = category.getName().equalsIgnoreCase("status") ? Text.literal("—")
                : move.getPower() > 0 ? Text.literal(Integer.toString((int) move.getPower()))
                : Text.translatable("stateditor.move.see_effect");
        Text accuracy = Text.literal(move.getAccuracy() > 0 ? (int) move.getAccuracy() + "%" : "—");
        String pp = Integer.toString(move.getPp());
        if (pokemon != null) {
            for (var known : pokemon.getMoveSet()) {
                if (known.getTemplate().equals(move)) {
                    pp = known.getCurrentPp() + "/" + known.getMaxPp();
                    break;
                }
            }
        }
        Text[] labels = {powerLabel, Text.translatable("cobblemon.ui.accuracy"), Text.literal("PP")};
        Text[] values = {power, accuracy, Text.literal(pp)};
        int columnSpace = width - 2 * PAD - 2 * GAP;
        int[] naturalWidths = new int[labels.length];
        int required = 0;
        for (int i = 0; i < labels.length; i++) {
            values[i] = values[i].copy().formatted(Formatting.BOLD);
            naturalWidths[i] = Math.max(font.getWidth(labels[i]), font.getWidth(values[i])) + 10;
            required += naturalWidths[i];
        }
        int metricLines = 0;
        int used = 0;
        for (int i = 0; i < labels.length; i++) {
            int columnWidth = required <= columnSpace ? naturalWidths[i] + (columnSpace - required) / labels.length
                    : columnSpace / labels.length;
            if (i == labels.length - 1) columnWidth = columnSpace - used;
            used += columnWidth;
            var metric = new Metric(columnWidth, font.wrapLines(labels[i], columnWidth - 8),
                    font.wrapLines(values[i], columnWidth - 8));
            metrics.add(metric);
            metricLines = Math.max(metricLines, metric.label.size() + metric.value.size());
        }
        metricHeight = metricLines * font.fontHeight + 8;

        var description = move.getDescription();
        effect = font.wrapLines(description, width - 2 * PAD - 4);
        int fixedHeight = PAD + headerHeight + GAP + metricHeight + 2 * PAD + 3;
        var browse = font.wrapLines(BROWSE_HINT, width - 2 * PAD - 4);
        boolean overflow = fixedHeight + (effect.size() + browse.size()) * font.fontHeight > screenHeight - 2 * MARGIN;
        footer = overflow ? font.wrapLines(Text.translatable("stateditor.move.scroll"), width - 2 * PAD - 4) : browse;
        fixedHeight += footer.size() * font.fontHeight;
        visibleLines = Math.min(effect.size(), Math.max(1, (screenHeight - 2 * MARGIN - fixedHeight) / font.fontHeight));
        height = fixedHeight + visibleLines * font.fontHeight;
        var spoken = name.copy().append(". ").append(subtitle);
        for (int i = 0; i < labels.length; i++) spoken.append(". ").append(labels[i]).append(" ").append(values[i]);
        narration = spoken.append(". ").append(description).append(". ").append(BROWSE_HINT);
    }

    Text narration() {
        return narration;
    }

    boolean scroll(int lines) {
        if (visibleLines == effect.size()) return false;
        firstLine = Math.clamp(firstLine + lines, 0, effect.size() - visibleLines);
        return true;
    }

    void draw(DrawContext ctx, TextFieldWidget field, int anchorX, int screenWidth, int screenHeight) {
        int x = Math.clamp(anchorX + 6, MARGIN, screenWidth - width - MARGIN);
        int y = field.getBottom() + GAP;
        if (y + height > screenHeight - MARGIN) {
            y = field.getY() - GAP - height;
            if (y < MARGIN) {
                if (field.getX() >= width + MARGIN + GAP) x = field.getX() - width - GAP;
                else if (anchorX >= width + MARGIN + GAP) x = anchorX - width - GAP;
            }
        }
        y = Math.clamp(y, MARGIN, Math.max(MARGIN, screenHeight - height - MARGIN));
        var matrices = ctx.getMatrices();
        ctx.draw();
        matrices.push();
        try {
            matrices.translate(x, y, 400);
            PokeEditScreen.cutCorners(ctx, 0, 0, width, height, LINE);
            PokeEditScreen.cutCorners(ctx, 1, 1, width - 1, height - 1, BODY);
            int bottom = PAD + headerHeight;
            ctx.fill(PAD, PAD, width - PAD, bottom, 0xFF403746);
            ctx.drawBorder(PAD, PAD, width - 2 * PAD, headerHeight, 0xFF78687F);
            ctx.fill(PAD, PAD, PAD + 2, bottom, accent);
            drawLines(ctx, title, 2 * PAD, PAD + 3, INK);
            icon.render(ctx);
            drawLines(ctx, classification, 2 * PAD + 15, bottom - classification.size() * font.fontHeight - PAD, MUTED);

            int yMetric = bottom + GAP;
            int left = PAD;
            for (var metric : metrics) {
                int right = left + metric.width;
                ctx.fill(left, yMetric, right, yMetric + metricHeight, 0xFF302A39);
                ctx.drawBorder(left, yMetric, right - left, metricHeight, LINE);
                drawLines(ctx, metric.label, left + 4, yMetric + 3, MUTED);
                drawLines(ctx, metric.value, left + 4, yMetric + metricHeight - metric.value.size() * font.fontHeight - 3, INK);
                left = right + GAP;
            }
            int bodyY = yMetric + metricHeight + PAD;
            drawLines(ctx, effect.subList(firstLine, firstLine + visibleLines), PAD + 2, bodyY, INK);
            int footerY = bodyY + visibleLines * font.fontHeight + 3;
            ctx.fill(PAD, footerY, width - PAD, footerY + 1, LINE);
            drawLines(ctx, footer, PAD + 2, footerY + 3, MUTED);
            ctx.draw();
        } finally {
            matrices.pop();
        }
    }

    private void drawLines(DrawContext ctx, List<OrderedText> lines, int x, int y, int color) {
        for (var line : lines) {
            ctx.drawText(font, line, x, y, color, false);
            y += font.fontHeight;
        }
    }
}
