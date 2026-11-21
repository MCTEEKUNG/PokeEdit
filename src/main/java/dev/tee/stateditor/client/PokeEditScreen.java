package dev.tee.stateditor.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.function.Supplier;

/** Shared controls for the transparent party editor and its chooser. */
public abstract class PokeEditScreen extends Screen {
    protected static final int TEXT_COLOR = 0xFCFAFF;
    protected static final int SECONDARY_COLOR = 0xE3DEED;
    protected static final int ACCENT = 0xE4D5FF;
    protected static final int ACCENT_EDGE = 0xFFCFB1FF;
    protected static final int ACCENT_INK = 0x332146;
    protected static final int RULE = 0x66E1D8EB;

    protected PokeEditScreen(Text title) {
        super(title);
    }

    @Override
    public void setFocused(Element child) {
        // A pressed button can rebuild the screen before Minecraft assigns it mouse focus.
        if (child == null || children().contains(child)) super.setFocused(child);
    }

    @Override
    public void resize(MinecraftClient client, int width, int height) {
        if (!(getFocused() instanceof TextFieldWidget old)) {
            super.resize(client, width, height);
            return;
        }
        int caret = old.getCursor();
        // The widget exposes the selected text but not its anchor. With the caret at zero,
        // that selection is the prefix ending at the anchor, including for backward selections.
        old.setSelectionStart(0);
        int anchor = old.getSelectedText().length();
        old.setSelectionStart(caret);
        super.resize(client, width, height);
        for (var child : children()) {
            if (child instanceof TextFieldWidget field && field.getMessage().equals(old.getMessage())) {
                setFocused(field);
                if (!field.getText().equals(old.getText())) field.setText(old.getText());
                field.setSelectionStart(caret);
                field.setSelectionEnd(anchor);
                break;
            }
        }
    }

    protected enum Style { ACTION, QUIET, ROW, CURRENT_ROW }

    protected ButtonWidget button(int x, int y, int w, int h, Text text, Style style, Runnable action) {
        return addDrawableChild(new ButtonWidget(x, y, w, h, text, b -> action.run(), Supplier::get) {
            @Override
            protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
                boolean highlight = active && isSelected();
                int color = active ? TEXT_COLOR : 0xBEC9CF;
                switch (style) {
                    case ACTION -> {
                        cutCorners(ctx, getX(), getY(), getRight(), getBottom(),
                                active ? (highlight ? 0xFFF0E8FF : ACCENT_EDGE) : 0x88282035);
                        if (active) {
                            ctx.drawText(textRenderer, getMessage(), getX() + (getWidth() - textRenderer.getWidth(getMessage())) / 2,
                                    getY() + (getHeight() - 8) / 2, ACCENT_INK, false);
                            return;
                        }
                    }
                    case ROW, CURRENT_ROW -> {
                        boolean chosen = style == Style.CURRENT_ROW;
                        cutCorners(ctx, getX(), getY(), getRight(), getBottom(),
                                chosen ? 0xA147355E : highlight ? 0x962D263B : 0x68241E30);
                        if (chosen || highlight) ctx.fill(getX(), getY() + 2, getX() + 2, getBottom() - 2, ACCENT_EDGE);
                        drawScrollableText(ctx, textRenderer, getMessage(), getX() + 6, getX() + 6, getY(),
                                getRight() - 5, getBottom(), chosen || highlight ? ACCENT : color);
                        return;
                    }
                    case QUIET -> {
                        if (highlight) {
                            color = ACCENT;
                            ctx.fill(getX() + 2, getBottom() - 1, getRight() - 2, getBottom(), ACCENT_EDGE);
                        }
                    }
                }
                drawScrollableText(ctx, textRenderer, getMessage(), getX() + 2, getY(), getRight() - 2, getBottom(), color);
            }
        });
    }

    protected static void cutCorners(DrawContext ctx, int x, int y, int right, int bottom, int color) {
        ctx.fill(x + 1, y, right - 1, y + 1, color);
        ctx.fill(x, y + 1, right, bottom - 1, color);
        ctx.fill(x + 1, bottom - 1, right - 1, bottom, color);
    }

    protected TextFieldWidget input(int x, int y, int w, int h, String label) {
        var widget = new TextFieldWidget(textRenderer, x, y, w, h, Text.literal(label)) {
            @Override
            public int getInnerWidth() {
                return getWidth() - 8;
            }

            @Override
            public void onClick(double mouseX, double mouseY) {
                super.onClick(mouseX - 4, mouseY);
            }

            @Override
            public void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
                cutCorners(ctx, getX(), getY(), getRight(), getBottom(),
                        isFocused() ? 0xA132263E : isHovered() ? 0x90251F33 : 0x73221F2B);
                ctx.fill(getX() + 2, getBottom() - 1, getRight() - 2, getBottom(),
                        isFocused() ? ACCENT_EDGE : isHovered() ? 0xFFD7CBE8 : RULE);
                if (isFocused()) ctx.fill(getX(), getY() + 2, getX() + 2, getBottom() - 2, ACCENT_EDGE);
                ctx.enableScissor(getX() + 2, getY(), getRight() - 2, getBottom());
                var matrices = ctx.getMatrices();
                matrices.push();
                matrices.translate(4, (getHeight() - 8) / 2, 0);
                try {
                    super.renderWidget(ctx, mouseX, mouseY, delta);
                } finally {
                    matrices.pop();
                    ctx.disableScissor();
                }
            }
        };
        widget.setDrawsBackground(false);
        widget.setEditableColor(TEXT_COLOR);
        return addDrawableChild(widget);
    }

    protected void heading(DrawContext ctx, int x, int y) {
        var matrices = ctx.getMatrices();
        matrices.push();
        matrices.translate(x, y, 0);
        matrices.scale(1.4f, 1.4f, 1f);
        ctx.drawTextWithShadow(textRenderer, getTitle(), 0, 0, TEXT_COLOR);
        matrices.pop();
    }

    protected void label(DrawContext ctx, String text, int x, int y, int availableWidth, int color) {
        ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(text, availableWidth), x, y, color);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
