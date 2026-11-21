package dev.tee.stateditor.client;

import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.storage.ClientParty;
import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.tee.stateditor.StatEditorClient;
import dev.tee.stateditor.StatEditorMod;
import dev.tee.stateditor.config.StatEditorConfig;
import dev.tee.stateditor.data.PokemonLookup;
import dev.tee.stateditor.data.StatIds;
import dev.tee.stateditor.data.StatMath;
import dev.tee.stateditor.data.StatOverrideStore;
import dev.tee.stateditor.net.SetStatPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.text.OrderedText;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The stat editor. It shows the player's party on the left and the stats of the selected Pokémon on
 * the right. Every change is sent to the server straight away, so the values update in real time
 * for everybody that can see the Pokémon.
 */
public class StatEditorScreen extends Screen {
    private static final int LIST_WIDTH = 136;
    private static final int LIST_X = 8;
    private static final int LIST_ENTRY_HEIGHT = 26;
    private static final int PARTY_Y = 146;
    private static final int SLOT_WIDTH = LIST_WIDTH / 2;
    /** The size of the little window that shows the Pokémon of a party slot. */
    private static final int MODEL_SIZE = 24;
    private static final int PREVIEW_Y = 39;
    private static final int PREVIEW_HEIGHT = 88;
    private static final int ROW_HEIGHT = 26;
    private static final int LABEL_WIDTH = 84;
    private static final int FIELD_WIDTH = 44;
    private static final int BUTTON_GAP = 2;
    private static final int BUTTON_COUNT = 5;

    private static StatEditorScreen openScreen;

    /** The Pokémon entity in the world that is being edited, or {@code -1} for the own party. */
    private final int targetEntityId;
    /** The party member to focus on, or {@code null}. */
    private final UUID focusPokemon;

    private final List<StatRow> rows = new ArrayList<>();
    /** The widget that draws the live Pokémon of every party slot. */
    private final FittedPokemonPreview[] modelWidgets = new FittedPokemonPreview[6];
    private final UUID[] modelPokemonIds = new UUID[6];
    private FittedPokemonPreview preview;
    private UUID previewPokemonId;
    private boolean selectionInitialized;
    private boolean updatingFields;
    /** Editing focus belongs to this Pokémon, not to the mutable party slot number. */
    private UUID displayedPokemonId;

    private int selectedSlot;
    private int listX = LIST_X;
    private int panelX = LIST_X + LIST_WIDTH + 8;
    private int buttonWidth = 24;

    public StatEditorScreen(int targetEntityId, UUID focusPokemon) {
        super(Text.translatable("stateditor.screen.title"));
        this.targetEntityId = targetEntityId;
        this.focusPokemon = focusPokemon;
    }

    @Override
    protected void init() {
        flushPendingEdits();
        this.setFocused(null);
        this.displayedPokemonId = null;
        openScreen = this;
        if (!this.selectionInitialized) {
            this.selectedSlot = initialSlot();
            this.selectionInitialized = true;
        }
        this.rows.clear();
        java.util.Arrays.fill(this.modelWidgets, null);
        java.util.Arrays.fill(this.modelPokemonIds, null);
        this.preview = null;
        this.previewPokemonId = null;

        // the panel has to fit into the scaled screen, so the button width is calculated
        int buttonWidth = buttonWidth();
        int contentWidth = LIST_WIDTH + 12 + LABEL_WIDTH + FIELD_WIDTH + 4
                + BUTTON_COUNT * buttonWidth + (BUTTON_COUNT - 1) * BUTTON_GAP;
        int left = Math.max(4, (this.width - contentWidth) / 2);
        this.listX = left;
        this.panelX = left + LIST_WIDTH + 12;
        this.buttonWidth = buttonWidth;

        int fieldX = this.panelX + LABEL_WIDTH;
        int buttonX = fieldX + FIELD_WIDTH + 4;

        int y = 46;
        for (Stat stat : StatIds.EDITABLE) {
            StatRow row = new StatRow(stat);
            row.field = new TextFieldWidget(this.textRenderer, fieldX, y, FIELD_WIDTH, 16, Text.empty());
            row.field.setMaxLength(10);
            row.field.setTextPredicate(text -> text.chars().allMatch(Character::isDigit));
            row.field.setPlaceholder(Text.literal("0"));
            row.field.setChangedListener(text -> stageEdit(row, text));
            this.addDrawableChild(row.field);

            row.minusTen = addButton(buttonX, y, "-10", (button) -> step(row, -10));
            row.minusOne = addButton(buttonX + buttonWidth + BUTTON_GAP, y, "-1", (button) -> step(row, -1));
            row.plusOne = addButton(buttonX + (buttonWidth + BUTTON_GAP) * 2, y, "+1", (button) -> step(row, 1));
            row.plusTen = addButton(buttonX + (buttonWidth + BUTTON_GAP) * 3, y, "+10", (button) -> step(row, 10));
            row.reset = addButton(buttonX + (buttonWidth + BUTTON_GAP) * 4, y, "×", (button) -> clear(row));
            this.rows.add(row);
            y += ROW_HEIGHT;
        }

        addButton(this.panelX, y + 4, 90, Text.translatable("stateditor.screen.reset_all"), (button) -> resetAll());
        addButton(this.panelX + 96, y + 4, 60, Text.translatable("gui.done"), (button) -> close());

        refresh();
    }

    /** @return the width of the small step buttons, so that everything fits on the screen. */
    private int buttonWidth() {
        int available = this.width - 8 - LIST_WIDTH - 12 - LABEL_WIDTH - FIELD_WIDTH - 4;
        int width = (available - (BUTTON_COUNT - 1) * BUTTON_GAP) / BUTTON_COUNT;
        return Math.max(14, Math.min(24, width));
    }

    private ButtonWidget addButton(int x, int y, String label, ButtonWidget.PressAction action) {
        return this.addDrawableChild(ButtonWidget.builder(Text.literal(label), action)
                .dimensions(x, y, this.buttonWidth, 16)
                .build());
    }

    private ButtonWidget addButton(int x, int y, int width, Text label, ButtonWidget.PressAction action) {
        return this.addDrawableChild(ButtonWidget.builder(label, action)
                .dimensions(x, y, width, 16)
                .build());
    }

    private int initialSlot() {
        ClientParty party = ownParty();
        if (party == null || this.focusPokemon == null) {
            return 0;
        }
        for (int slot = 0; slot < 6; slot++) {
            Pokemon pokemon = party.get(slot);
            if (pokemon != null && this.focusPokemon.equals(pokemon.getUuid())) {
                return slot;
            }
        }
        return 0;
    }

    /** @return the party of the local player. */
    public static ClientParty ownParty() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) {
                return null;
            }
            var storage = CobblemonClient.INSTANCE.getStorage();
            ClientParty party = storage.getPartyStores().get(client.player.getUuid());
            return party != null ? party : storage.getParty();
        } catch (Throwable throwable) {
            StatEditorMod.LOGGER.warn("Could not access the party", throwable);
            return null;
        }
    }

    /** @return the Pokémon that is currently being edited. */
    public Pokemon selectedPokemon() {
        if (this.targetEntityId != PokemonLookup.NO_ENTITY) {
            return StatEditorClient.findEntityPokemon(this.targetEntityId);
        }
        ClientParty party = ownParty();
        if (party == null) {
            return null;
        }
        return party.get(this.selectedSlot);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // the background blur is applied by Screen#render, so everything has to be drawn afterwards
        super.render(context, mouseX, mouseY, delta);

        drawReadableText(context, this.title, (this.width - this.textRenderer.getWidth(this.title)) / 2, 9, 0xFFFFFF);
        renderPartyList(context, mouseX, mouseY);
        // Draw after the translucent cards, not through them; each widget clips its own model.
        context.draw();
        for (FittedPokemonPreview widget : this.modelWidgets) {
            if (widget != null) widget.render(context, mouseX, mouseY, delta);
        }
        if (this.preview != null) this.preview.render(context, mouseX, mouseY, delta);

        drawReadableText(context, Text.translatable("stateditor.screen.autosave"), this.panelX,
                Math.min(this.height - 11, 229), 0xDCEAF5);

        Pokemon pokemon = selectedPokemon();
        if (pokemon == null) {
            return;
        }
        int y = 46;
        for (StatRow row : this.rows) {
            drawReadableText(context, StatIds.displayName(row.stat), this.panelX, y + 4, 0xFFFFFF);
            Integer override = StatOverrideStore.get(pokemon, row.stat);
            if (override != null && StatEditorConfig.showNaturalValues) {
                drawReadableText(context,
                        Text.translatable("stateditor.screen.natural", StatMath.naturalStat(pokemon, row.stat)),
                        this.panelX, y + 15, 0xD0DCE6);
            }
            y += ROW_HEIGHT;
        }
    }

    /**
     * Draws the party list. Every entry shows the live model of the Pokémon, its name and its level.
     * <p>
     * The preview renderer fits each animated mesh independently of its profile/world scale.
     */
    private void renderPartyList(DrawContext context, int mouseX, int mouseY) {
        ClientParty party = ownParty();
        Pokemon selectedPokemon = selectedPokemon();
        if (selectedPokemon != null) {
            Text name = selectedPokemon.getNickname() != null ? selectedPokemon.getNickname()
                    : selectedPokemon.getSpecies().getTranslatedName();
            Text label = Text.literal(this.textRenderer.trimToWidth(name.getString(), LIST_WIDTH - 4));
            drawReadableText(context, label, this.listX + (LIST_WIDTH - this.textRenderer.getWidth(label)) / 2,
                    28, 0xFFFFFF);
            Text level = Text.translatable("stateditor.screen.level", selectedPokemon.getLevel());
            drawReadableText(context, level, this.listX + (LIST_WIDTH - this.textRenderer.getWidth(level)) / 2,
                    130, 0xE5F0FF);
        }
        for (int slot = 0; slot < 6; slot++) {
            int x = this.listX + slot % 2 * SLOT_WIDTH;
            int y = PARTY_Y + slot / 2 * LIST_ENTRY_HEIGHT;
            boolean hovered = mouseX >= x && mouseX < x + SLOT_WIDTH - 2 && mouseY >= y && mouseY < y + LIST_ENTRY_HEIGHT - 2;
            boolean selected = this.targetEntityId == PokemonLookup.NO_ENTITY && slot == this.selectedSlot;
            int color = selected ? 0xCC244D73 : (hovered ? 0xBB1C3044 : 0xA5101925);
            context.fill(x, y, x + SLOT_WIDTH - 2, y + LIST_ENTRY_HEIGHT - 2, color);

            Pokemon pokemon = party != null ? party.get(slot) : null;
            if (pokemon != null) {
                Text name = pokemon.getNickname() != null
                        ? pokemon.getNickname()
                        : pokemon.getSpecies().getTranslatedName();
                String label = this.textRenderer.trimToWidth(name.getString(), SLOT_WIDTH - MODEL_SIZE - 8);
                context.drawTextWithShadow(this.textRenderer, Text.literal(label),
                        x + MODEL_SIZE + 3, y + 3, 0xFFFFFF);
                context.drawTextWithShadow(this.textRenderer, "Lv." + pokemon.getLevel(),
                        x + MODEL_SIZE + 3, y + 14, 0xD6E4F2);
            } else {
                context.drawTextWithShadow(this.textRenderer,
                        Text.translatable("stateditor.screen.empty_short", slot + 1),
                        x + 4, y + 9, 0xB8C7D8);
            }
        }
    }

    private void drawReadableText(DrawContext context, Text text, int x, int y, int color) {
        drawReadableText(context, text.asOrderedText(), x, y, color);
    }

    private void drawReadableText(DrawContext context, OrderedText text, int x, int y, int color) {
        ReadableLabel.draw(context, this.textRenderer, text, x, y, color);
    }

    private PreviewArea previewArea() {
        return new PreviewArea(this.listX, PREVIEW_Y, LIST_WIDTH, PREVIEW_HEIGHT);
    }

    /**
     * Creates the widget that shows the live model of every party member and keeps them up to date.
     * The models are animated by Cobblemon, so the Pokémon in the list are moving while the editor
     * is open.
     */
    private void refreshModels() {
        ClientParty party = ownParty();
        for (int slot = 0; slot < this.modelWidgets.length; slot++) {
            Pokemon pokemon = party != null ? party.get(slot) : null;
            FittedPokemonPreview widget = this.modelWidgets[slot];
            if (pokemon == null) {
                this.modelWidgets[slot] = null;
                this.modelPokemonIds[slot] = null;
                continue;
            }
            var renderable = pokemon.asRenderablePokemon();
            int x = this.listX + slot % 2 * SLOT_WIDTH + 1;
            int y = PARTY_Y + slot / 2 * LIST_ENTRY_HEIGHT;
            if (widget == null || !pokemon.getUuid().equals(this.modelPokemonIds[slot])) {
                widget = new FittedPokemonPreview(renderable, x, y, MODEL_SIZE, MODEL_SIZE, 2);
                this.modelWidgets[slot] = widget;
                this.modelPokemonIds[slot] = pokemon.getUuid();
            } else {
                // the screen can be resized, so the model has to follow the party list
                widget.update(renderable, x, y);
            }
        }
        Pokemon selected = selectedPokemon();
        if (selected == null) {
            this.preview = null;
            this.previewPokemonId = null;
        } else if (this.preview == null || !selected.getUuid().equals(this.previewPokemonId)) {
            PreviewArea area = previewArea();
            this.preview = new FittedPokemonPreview(selected.asRenderablePokemon(), area.x(), area.y(), area.width(), area.height(), 6);
            this.previewPokemonId = selected.getUuid();
        } else {
            this.preview.update(selected.asRenderablePokemon(), this.listX, PREVIEW_Y);
        }
    }

    @Override
    public void tick() {
        for (StatRow row : this.rows) flushEdit(row, !row.field.isFocused());
        // the party can change while the editor is open, so keep the values up to date
        refresh();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.targetEntityId == PokemonLookup.NO_ENTITY) {
            for (int slot = 0; slot < 6; slot++) {
                int x = this.listX + slot % 2 * SLOT_WIDTH;
                int y = PARTY_Y + slot / 2 * LIST_ENTRY_HEIGHT;
                if (mouseX >= x && mouseX < x + SLOT_WIDTH - 2
                        && mouseY >= y && mouseY < y + LIST_ENTRY_HEIGHT - 2) {
                    flushPendingEdits();
                    this.setFocused(null);
                    this.selectedSlot = slot;
                    refresh();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER)
                && this.getFocused() instanceof TextFieldWidget field) {
            applyField(field);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void applyField(TextFieldWidget field) {
        for (StatRow row : this.rows) {
            if (row.field == field) {
                stageEdit(row, field.getText());
                flushEdit(row, true);
                return;
            }
        }
    }

    private void step(StatRow row, int amount) {
        Pokemon pokemon = editingPokemon();
        if (pokemon == null) {
            return;
        }
        int current = pokemon.getStat(row.stat);
        try {
            int typed = Integer.parseInt(row.field.getText());
            if (StatEditorConfig.isValidValue(typed)) current = typed;
        } catch (NumberFormatException ignored) {
        }
        row.pending.cancel();
        this.setFocused(null);
        long next = (long) current + amount;
        send(row, (int) Math.max(StatEditorConfig.minValue, Math.min(StatEditorConfig.maxValue, next)), false);
    }

    private void clear(StatRow row) {
        if (editingPokemon() == null) return;
        row.pending.cancel();
        this.setFocused(null);
        send(row, 0, true);
    }

    private void resetAll() {
        Pokemon pokemon = editingPokemon();
        if (pokemon == null) {
            return;
        }
        for (StatRow row : this.rows) row.pending.cancel();
        this.setFocused(null);
        for (Stat stat : StatIds.EDITABLE) {
            send(stat, 0, true);
        }
    }

    private void send(StatRow row, int value, boolean clear) {
        send(row.stat, value, clear);
    }

    private void send(Stat stat, int value, boolean clear) {
        Pokemon pokemon = editingPokemon();
        if (pokemon == null) {
            return;
        }
        send(pokemon.getUuid(), this.targetEntityId, stat, value, clear);
    }

    private void send(UUID pokemonId, int entityId, Stat stat, int value, boolean clear) {
        if (!ClientPlayNetworking.canSend(SetStatPayload.ID)) return;
        if (!clear && !StatEditorConfig.isValidValue(value)) {
            value = Math.max(StatEditorConfig.minValue, Math.min(StatEditorConfig.maxValue, value));
        }
        ClientPlayNetworking.send(new SetStatPayload(pokemonId, entityId,
                StatIds.key(stat), value, clear));
    }

    /** Refreshes the values shown on the currently open editor. */
    public static void refreshOpenScreen() {
        if (openScreen != null) {
            openScreen.refresh();
        }
    }

    /** Re-reads the values of the selected Pokémon. */
    public void refresh() {
        Pokemon pokemon = selectedPokemon();
        UUID selectedId = pokemon == null ? null : pokemon.getUuid();
        if (!java.util.Objects.equals(this.displayedPokemonId, selectedId)) {
            flushPendingEdits();
            this.setFocused(null);
            this.displayedPokemonId = selectedId;
        }
        refreshModels();
        this.updatingFields = true;
        try {
            for (StatRow row : this.rows) {
                if (pokemon == null) {
                    row.pending.cancel();
                    row.field.setText("");
                    row.field.setEditable(false);
                    continue;
                }
                row.field.setEditable(true);
                Integer override = StatOverrideStore.get(pokemon, row.stat);
                int value = override != null ? override : pokemon.getStat(row.stat);
                String text = String.valueOf(value);
                // do not fight the player while they are typing
                if (!row.field.equals(this.getFocused()) && !text.equals(row.field.getText())) {
                    row.field.setText(text);
                    row.field.setEditableColor(0xFFFFFF);
                }
            }
        } finally {
            this.updatingFields = false;
        }
    }

    private void stageEdit(StatRow row, String text) {
        if (this.updatingFields) return;
        Pokemon pokemon = editingPokemon();
        if (pokemon == null) return;
        boolean valid = row.pending.update(pokemon.getUuid(), this.targetEntityId, text,
                StatEditorConfig.minValue, StatEditorConfig.maxValue, Util.getMeasuringTimeMs());
        row.field.setEditableColor(valid || text.isEmpty() ? 0xFFFFFF : 0xFF7777);
    }

    private Pokemon editingPokemon() {
        Pokemon pokemon = selectedPokemon();
        if (pokemon == null || !pokemon.getUuid().equals(this.displayedPokemonId)) {
            // A server party swap can happen between tick() and a keystroke/button click.
            refresh();
            return null;
        }
        return pokemon;
    }

    private void flushEdit(StatRow row, boolean force) {
        PendingStatEdit.Edit pending = row.pending.take(Util.getMeasuringTimeMs(), force);
        if (pending != null) send(pending.pokemonId(), pending.entityId(), row.stat, pending.value(), false);
    }

    private void flushPendingEdits() {
        for (StatRow row : this.rows) flushEdit(row, true);
    }

    @Override
    public void close() {
        flushPendingEdits();
        super.close();
    }

    @Override
    public void removed() {
        flushPendingEdits();
        openScreen = null;
    }

    private static final class StatRow {
        private final Stat stat;
        private final PendingStatEdit pending = new PendingStatEdit();
        private TextFieldWidget field;
        private ButtonWidget minusTen;
        private ButtonWidget minusOne;
        private ButtonWidget plusOne;
        private ButtonWidget plusTen;
        private ButtonWidget reset;

        private StatRow(Stat stat) {
            this.stat = stat;
        }
    }
}
