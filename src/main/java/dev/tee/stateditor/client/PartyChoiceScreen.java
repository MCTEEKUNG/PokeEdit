package dev.tee.stateditor.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;

/** A searchable menu anchored to the nature selector on the Stats tab. */
final class PartyChoiceScreen extends PokeEditScreen {
    private static final int ROW_HEIGHT = 17;
    private static final int LIST_TOP = 24;
    private static final int FOOTER_HEIGHT = 20;
    private final PartyEditorScreen parent;
    private final List<Choice> choices;
    private List<Choice> matches;
    private TextFieldWidget search;
    private String query = "";
    private int page = -1;
    private ButtonWidget firstResult;
    private int pages;
    private int rows;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;

    PartyChoiceScreen(PartyEditorScreen parent, List<Choice> choices) {
        super(Text.literal("Choose nature"));
        this.parent = parent;
        this.choices = choices;
    }

    @Override
    protected void init() {
        if (parent.width != width || parent.height != height) parent.resize(client, width, height);
        var anchor = parent.natureSelector();
        panelWidth = Math.min(width - 8, Math.max(214, anchor.getWidth()));
        left = Math.clamp(anchor.getRight() - panelWidth, 4, width - panelWidth - 4);
        int above = anchor.getY() - 6;
        int below = height - anchor.getBottom() - 6;
        rows = Math.clamp((Math.max(above, below) - LIST_TOP - FOOTER_HEIGHT) / ROW_HEIGHT, 1, 6);
        panelHeight = LIST_TOP + rows * ROW_HEIGHT + FOOTER_HEIGHT;
        int preferredTop = below >= above ? anchor.getBottom() + 2 : anchor.getY() - 2 - panelHeight;
        top = Math.clamp(preferredTop, 2, Math.max(2, height - panelHeight - 2));
        search = input(left + 4, top + 4, panelWidth - 26, 17, "Search natures by name or stat");
        search.setMaxLength(64);
        search.setSuggestion(query.isEmpty() ? "Search name or stat" : null);
        search.setText(query);
        search.setChangedListener(text -> {
            query = text;
            search.setSuggestion(text.isEmpty() ? "Search name or stat" : null);
            page = 0;
            rebuildResults();
        });
        setInitialFocus(search);
        rebuildResults();
    }

    private void rebuildResults() {
        children().stream().filter(child -> child instanceof ButtonWidget).toList().forEach(this::remove);
        String key = query.strip().toLowerCase(Locale.ROOT);
        matches = choices.stream().filter(choice -> choice.searchText().toLowerCase(Locale.ROOT).contains(key)).toList();
        pages = Math.max(1, (matches.size() + rows - 1) / rows);
        if (page < 0) {
            int current = 0;
            while (current < matches.size() && !matches.get(current).current()) current++;
            page = current == matches.size() ? 0 : current / rows;
        }
        page = Math.min(page, pages - 1);
        firstResult = null;
        int start = page * rows;
        for (int i = start; i < Math.min(start + rows, matches.size()); i++) {
            var choice = matches.get(i);
            var result = button(left + 4, top + LIST_TOP + (i - start) * ROW_HEIGHT, panelWidth - 8, ROW_HEIGHT - 1, choice.label(),
                    choice.current() ? Style.CURRENT_ROW : Style.ROW, () -> select(choice));
            if (choice.current()) result.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("Current nature")));
            if (i == start) firstResult = result;
        }
        int footer = top + panelHeight - FOOTER_HEIGHT + 1;
        int center = left + panelWidth / 2;
        button(center - 40, footer, 18, 16, Text.literal("‹"), Style.QUIET, () -> changePage(-1)).active = page > 0;
        button(center + 22, footer, 18, 16, Text.literal("›"), Style.QUIET, () -> changePage(1)).active = page + 1 < pages;
        var close = button(left + panelWidth - 21, top + 4, 17, 17, Text.literal("×"), Style.QUIET, this::close);
        close.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("Close (Esc)")));
    }

    private void changePage(int step) {
        int next = Math.clamp(page + step, 0, pages - 1);
        if (next != page) {
            page = next;
            setFocused(search);
            rebuildResults();
        }
    }

    private void select(Choice choice) {
        choice.select().run();
        close();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_DOWN && search.isFocused() && firstResult != null) {
            setFocused(firstResult);
            return true;
        }
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                && search.isFocused() && !matches.isEmpty()) {
            select(matches.get(page * rows));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX < left || mouseX >= left + panelWidth || mouseY < top || mouseY >= top + panelHeight) {
            close();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (vertical != 0 && mouseX >= left && mouseX < left + panelWidth
                && mouseY >= top + LIST_TOP && mouseY < top + panelHeight - FOOTER_HEIGHT) {
            changePage(vertical < 0 ? 1 : -1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        parent.render(context, -1, -1, delta);
        context.draw();
        var matrices = context.getMatrices();
        matrices.push();
        matrices.translate(0, 0, 300);
        try {
            super.render(context, mouseX, mouseY, delta);
            context.draw();
        } finally {
            matrices.pop();
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        cutCorners(context, left, top, left + panelWidth, top + panelHeight, 0xF92C2635);
        context.fill(left + 1, top, left + panelWidth - 1, top + 1, ACCENT_EDGE);
        if (matches.isEmpty()) {
            label(context, "No matching nature", left + 9, top + LIST_TOP + 4, panelWidth - 18, SECONDARY_COLOR);
        }
        int footer = top + panelHeight - FOOTER_HEIGHT;
        context.fill(left + 4, footer, left + panelWidth - 4, footer + 1, RULE);
        if (!matches.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, (page + 1) + " / " + pages, left + panelWidth / 2, footer + 5, TEXT_COLOR);
        }
    }

    @Override
    public void close() {
        client.setScreen(parent);
        parent.setFocused(parent.natureSelector());
    }

    record Choice(String searchText, Text label, boolean current, Runnable select) {}
}
