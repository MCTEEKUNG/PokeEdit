package dev.tee.stateditor.client;

import com.cobblemon.mod.common.api.pokemon.Natures;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.client.gui.TypeIcon;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.net.serverhandling.pokemon.update.SetNicknameHandler;
import com.cobblemon.mod.common.pokemon.Nature;
import com.cobblemon.mod.common.pokemon.Species;
import com.cobblemon.mod.common.pokemon.RenderablePokemon;
import dev.tee.stateditor.config.StatEditorConfig;
import dev.tee.stateditor.data.StatIds;
import dev.tee.stateditor.data.StatMath;
import dev.tee.stateditor.net.PartySnapshot;
import dev.tee.stateditor.net.SaveParty;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.Language;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

public final class PartyEditorScreen extends PokeEditScreen {
    private static final Identifier EDITOR_ICON = Identifier.of("stateditor", "textures/gui/pokeedit_icon.png");
    private static final int EDITOR_ICON_SIZE = 16;
    private static final int PARTY_SIZE = 6;
    private static final int GREEN = 0x81EBA2;
    private static final int RED = 0xFF8B93;
    private static final int WIDGET_HEIGHT = 19;
    private static final String[] STAT_LABELS = {"HP", "Attack", "Defense", "Sp. Atk", "Sp. Def", "Speed"};

    private final List<Species> allSpecies = PokemonSpecies.getImplemented().stream().sorted(
            Comparator.comparing((Species species) -> species.getTranslatedName().getString(), String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(species -> species.getResourceIdentifier().toString())).toList();
    private final List<Nature> natures = Natures.all().stream().sorted(
            Comparator.comparing(PartyEditorScreen::natureName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(Nature::getName)).toList();
    private final FittedPokemonPreview[] previews = new FittedPokemonPreview[PARTY_SIZE];

    private PartySnapshot snapshot;
    private PartyDraft draft;
    private int selected;
    private Tab tab = Tab.POKEMON;
    private String status;
    private String validation = "";
    private boolean pending;
    private boolean timedOut;
    private long sentAt;
    private TextFieldWidget speciesField;
    private TextFieldWidget nicknameField;
    private final ButtonWidget[] tabs = new ButtonWidget[Tab.values().length];
    private ButtonWidget previousSpecies;
    private ButtonWidget nextSpecies;
    private ButtonWidget natureButton;
    private ButtonWidget save;
    private ButtonWidget reset;
    private FittedPokemonPreview portrait;
    private TypeIcon previewTypeIcon;
    private String previewTypes = "";
    private int typeTextX;
    private boolean dirty;
    private String speciesQuery = "";
    private boolean completingSpecies;

    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private int sideWidth;
    private int contentX;
    private int contentWidth;
    private int contentY;
    private int footerY;
    private int statRowHeight;
    private int statHeaderY;
    private int gridY;
    private int gridRowHeight;
    private int actionX;
    private boolean partyStrip;
    private boolean portraitInSlot;
    private boolean stackedFooter;
    private int formHeight;
    private int tabY;
    private int tabHeight;
    private int statRows;
    private int statPage;
    private int statToReveal = -1;
    private int moveStep;
    private final TextFieldWidget[] moveFields = new TextFieldWidget[PartyDraft.MOVE_COUNT];
    private final String[] moveQueries = new String[PartyDraft.MOVE_COUNT];
    private final TypeIcon[] moveIcons = new TypeIcon[PartyDraft.MOVE_COUNT];
    private final MoveDetailsCard[] moveCards = new MoveDetailsCard[PartyDraft.MOVE_COUNT];
    private int displayedMove = -1;
    private final List<MoveTemplate> moveCatalog = Moves.all().stream().sorted(
            Comparator.comparing((MoveTemplate move) -> move.getDisplayName().getString(), String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(MoveTemplate::getName)).toList();
    private boolean completingMove;

    public PartyEditorScreen(PartySnapshot snapshot) {
        super(Text.literal("PokéEdit"));
        this.snapshot = snapshot;
        status = snapshot.notice();
        for (int slot = 0; slot < PARTY_SIZE; slot++) {
            if (snapshot.party()[slot] != null) {
                selected = slot;
                break;
            }
        }
        resetDraft();
    }

    public void receive(PartySnapshot incoming) {
        if (!snapshot.token().equals(incoming.token()) || incoming.revision() < snapshot.revision()) {
            return;
        }
        boolean keepDraft = !incoming.saved() && !incoming.terminal()
                && draft.matchesOriginal(incoming.party()[selected])
                && Arrays.equals(snapshot.stats()[selected], incoming.stats()[selected])
                && Arrays.equals(snapshot.bonuses()[selected], incoming.bonuses()[selected]);
        snapshot = incoming;
        status = incoming.notice();
        pending = false;
        timedOut = false;
        if (!keepDraft) {
            resetDraft();
        }
        clearAndInit();
    }

    @Override
    public void resize(MinecraftClient client, int width, int height) {
        if (tab == Tab.STATS && getFocused() instanceof TextFieldWidget field) {
            String label = field.getMessage().getString();
            for (int i = 0; i < STAT_LABELS.length; i++) {
                if (label.startsWith(STAT_LABELS[i] + " ")) statToReveal = i;
            }
        }
        super.resize(client, width, height);
        statToReveal = -1;
    }

    @Override
    protected void init() {
        boolean shortWindow = height < 210;
        partyStrip = width < 320 && !shortWindow;
        panelWidth = Math.min(480, width - (width < 320 ? 12 : 24));
        panelHeight = Math.min(partyStrip ? 290 : 242, height - (shortWindow ? 6 : 16));
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        sideWidth = Math.min(146, panelWidth / (width < 320 ? 4 : 3));
        contentX = partyStrip ? left : left + sideWidth + (width < 320 ? 8 : 15);
        contentWidth = left + panelWidth - contentX;
        footerY = top + panelHeight - 20;
        stackedFooter = panelWidth < 305 && !shortWindow;
        int formBottom = footerY - (stackedFooter ? 26 : 8);
        portraitInSlot = partyStrip || height < 165;
        gridRowHeight = partyStrip ? 31 : portraitInSlot ? Math.clamp((formBottom - top - 42) / 2, 16, 34)
                : Math.clamp((panelHeight - 68) / 4, 24, 34);
        gridY = partyStrip ? top + 25 : formBottom - 2 - 2 * gridRowHeight;
        tabY = partyStrip ? gridY + gridRowHeight + 2 : top + (shortWindow ? 20 : 25);
        tabHeight = shortWindow ? 15 : 21;
        contentY = tabY + tabHeight + (shortWindow || partyStrip ? 5 : 12);
        formHeight = formBottom - contentY;
        statHeaderY = contentY + natureHeight() + 4;
        int statBodyHeight = formBottom - statHeaderY - 12;
        statRows = Math.clamp(statBodyHeight / 12, 1, PartyDraft.STAT_COUNT);
        statPage = statToReveal >= 0 ? statToReveal / statRows
                : Math.clamp(statPage, 0, (PartyDraft.STAT_COUNT - 1) / statRows);
        statRowHeight = Math.clamp(statBodyHeight / statRows, 10, 22);
        moveStep = Math.clamp(formHeight / PartyDraft.MOVE_COUNT, 12, 24);
        speciesField = null;
        nicknameField = null;
        portrait = null;
        save = null;
        reset = null;
        initParty();
        previousSpecies = nextSpecies = null;
        for (Tab target : Tab.values()) {
            int x = contentX + target.ordinal() * contentWidth / Tab.values().length;
            int endX = contentX + (target.ordinal() + 1) * contentWidth / Tab.values().length;
            tabs[target.ordinal()] = addDrawableChild(new SectionTab(target, x, endX - x));
        }

        switch (tab) {
            case POKEMON -> initPokemon();
            case STATS -> initStats();
            case MOVES -> initMoves();
        }

        int saveWidth = 66;
        int right = left + panelWidth;
        actionX = right - saveWidth - 75;
        reset = button(actionX, footerY, 69, WIDGET_HEIGHT, Text.literal("Reset draft"), Style.QUIET,
                () -> { resetDraft(); status = ""; clearAndInit(); });
        save = button(right - saveWidth, footerY, saveWidth, WIDGET_HEIGHT,
                Text.literal(pending ? "Saving…" : "Save"), Style.ACTION, this::submit);
        save.setTooltip(Tooltip.of(Text.literal("Save changes (Ctrl+S)")));
        var close = button(right - 18, top, 18, 17, Text.literal("×"), Style.QUIET, this::close);
        close.setTooltip(Tooltip.of(Text.literal("Close (Esc)")));
        close.active = !pending;
        updateSave();
    }

    private int slotX(int slot) {
        return left + (partyStrip ? slot * panelWidth / PARTY_SIZE : slot % 3 * sideWidth / 3);
    }

    private int slotWidth(int slot) {
        int end = partyStrip ? (slot + 1) * panelWidth / PARTY_SIZE : (slot % 3 + 1) * sideWidth / 3;
        return left + end - slotX(slot) - 2;
    }

    private int slotY(int slot) {
        return gridY + (partyStrip ? 0 : slot / 3 * gridRowHeight);
    }

    private void initParty() {
        for (int i = 0; i < PARTY_SIZE; i++) {
            var pokemon = snapshot.party()[i];
            String name = pokemon == null ? "Add Pokémon" : pokemon.getDisplayName(false).getString();
            int x = slotX(i);
            int y = slotY(i);
            int w = slotWidth(i);
            var slot = addDrawableChild(new PartySlotButton(i, x, y, w, Text.literal(name)));
            slot.active = canEdit();
            slot.setTooltip(Tooltip.of(Text.literal(pokemon == null ? name : name + " · Lv. " + pokemon.getLevel())));
            previews[i] = pokemon == null || (portraitInSlot && i == selected) ? null : new FittedPokemonPreview(pokemon.asRenderablePokemon(),
                    x + 2, y + 1, w - 4, gridRowHeight - 14, 1);
        }
        updatePortrait();
    }

    private void updatePortrait() {
        previewTypeIcon = null;
        if (draft.species == null || (!portraitInSlot && gridY - (top + 26) < 24)) {
            portrait = null;
            return;
        }
        var renderable = draft.original == null
                ? new RenderablePokemon(draft.species, Set.of(), ItemStack.EMPTY)
                : draft.original.asRenderablePokemon();
        if (renderable.getSpecies() != draft.species) {
            renderable = renderable.copy(draft.species, renderable.getAspects(), renderable.getHeldItem());
        }
        int x = left + 5;
        int y = top + 26;
        int w = sideWidth - 10;
        int h = gridY - y - 18;
        if (portraitInSlot) {
            x = slotX(selected) + 2;
            y = slotY(selected) + 1;
            w = slotWidth(selected) - 4;
            h = gridRowHeight - 14;
        }
        if (!portraitInSlot && gridY - y >= 70) {
            h -= 13;
            var form = renderable.getForm();
            var primary = form.getPrimaryType();
            var secondary = form.getSecondaryType();
            String types = primary.getDisplayName().getString();
            if (secondary != null) types += " / " + secondary.getDisplayName().getString();
            int iconSpace = secondary == null ? 13 : 22;
            previewTypes = textRenderer.trimToWidth(types, sideWidth - iconSpace - 3);
            int typeX = left + (sideWidth - iconSpace - textRenderer.getWidth(previewTypes)) / 2;
            typeTextX = typeX + iconSpace;
            previewTypeIcon = new TypeIcon(typeX + 4, gridY - 12, primary, secondary, false, true, 0f, 0f, 1f);
        }
        if (portrait == null) {
            portrait = new FittedPokemonPreview(renderable, x, y, w, h, portraitInSlot ? 1 : 4);
        } else {
            portrait.update(renderable, x, y);
        }
    }

    private String draftName() {
        if (draft.species == null) return draft.original == null ? "New Pokémon" : "Choose species";
        return draft.nickname.isBlank() ? draft.species.getTranslatedName().getString() : draft.nickname;
    }

    private void initPokemon() {
        int inset = inlineIdentity() ? 52 : 0;
        speciesField = field(contentX + inset, speciesY(), contentWidth - inset - 42, WIDGET_HEIGHT, "Species",
                draft.speciesText, "Species name or ID", 128, text -> {
                    draft.speciesText = text;
                    if (!completingSpecies) {
                        draft.species = findSpecies(text);
                        speciesQuery = draft.species == null ? text : "";
                    }
                    updatePortrait();
                    updateSave();
                });
        speciesField.setTooltip(Tooltip.of(Text.literal("Tab or Shift+Tab to browse species. Enter for nickname.")));
        previousSpecies = speciesArrow(contentX + contentWidth - 38, "‹", -1, "Previous species (Shift+Tab)");
        nextSpecies = speciesArrow(contentX + contentWidth - 17, "›", 1, "Next species (Tab)");
        nicknameField = field(contentX + inset, nicknameY(), contentWidth - inset, WIDGET_HEIGHT, "Nickname", draft.nickname,
                "Optional nickname", SetNicknameHandler.MAX_NAME_LENGTH, text -> {
                    draft.nickname = text;
                    updateSave();
                });
        if (draft.original == null && canEdit()) {
            setInitialFocus(speciesField);
        }
    }

    private boolean inlineIdentity() {
        return formHeight < 99;
    }

    private int speciesY() {
        return contentY + (inlineIdentity() ? 0 : 12);
    }

    private int nicknameY() {
        return contentY + (inlineIdentity() ? 24 : 54);
    }

    private void initStats() {
        initNatureSelector();
        int columnWidth = statColumnWidth();
        int first = statPage * statRows;
        if (statRows < PartyDraft.STAT_COUNT) {
            int arrowWidth = Math.min(20, statLabelWidth() / 2);
            statPageButton(contentX, arrowWidth, -1, first > 0);
            statPageButton(contentX + arrowWidth, arrowWidth, 1, first + statRows < PartyDraft.STAT_COUNT);
        }
        for (int i = first; i < Math.min(first + statRows, PartyDraft.STAT_COUNT); i++) {
            int row = i;
            int y = statHeaderY + 12 + (i - first) * statRowHeight;
            int x = contentX + statLabelWidth();
            int fieldHeight = Math.min(WIDGET_HEIGHT, statRowHeight - 1);
            String total = draft.original == null ? "Auto" : Integer.toString(snapshot.stats()[selected][i]);
            String iv = draft.currentIv(i) < 0 ? "Auto" : Integer.toString(draft.currentIv(i));
            TextFieldWidget totalField = numberField(x, y, columnWidth - 3, fieldHeight,
                    STAT_LABELS[i] + " total", draft.totals, row, total, 10);
            String hint = "Enter a total. Use 0 to clear the bonus.";
            if (draft.original != null && StatEditorConfig.showNaturalValues) {
                int natural = StatMath.naturalStat(draft.original, StatIds.EDITABLE.get(i));
                hint += "\nNatural total " + natural;
            }
            Integer bonus = snapshot.bonuses()[selected][i];
            if (bonus != null) {
                hint += "\nCurrent bonus " + (bonus >= 0 ? "+" : "") + bonus;
            }
            totalField.setTooltip(Tooltip.of(Text.literal(hint)));
            numberField(x + columnWidth, y, columnWidth - 3, fieldHeight,
                    STAT_LABELS[i] + " IV", draft.ivs, row, iv, 2);
            numberField(x + columnWidth * 2, y, columnWidth - 3, fieldHeight,
                    STAT_LABELS[i] + " EV", draft.evs, row, Integer.toString(draft.currentEv(i)), 3);
        }
    }

    private void statPageButton(int x, int w, int step, boolean enabled) {
        var arrow = button(x, statHeaderY - 2, w, 13, Text.literal(step < 0 ? "‹" : "›"), Style.QUIET, () -> {
            statPage += step;
            clearAndInit();
        });
        arrow.active = enabled;
        arrow.setTooltip(Tooltip.of(Text.literal(step < 0 ? "Previous stats" : "Next stats")));
    }

    private void initMoves() {
        displayedMove = -1;
        for (int i = 0; i < PartyDraft.MOVE_COUNT; i++) {
            int slot = i;
            var move = PartyDraft.findMove(draft.moves[i]);
            String value = move == null ? draft.moves[i] : move.getDisplayName().getString();
            moveQueries[i] = move == null ? value : "";
            var input = field(contentX + 52, contentY + i * moveStep, contentWidth - 52, Math.min(WIDGET_HEIGHT, moveStep - 2), "Move " + (i + 1),
                    value, "Type a move name", 64, text -> {
                        var chosen = PartyDraft.findMove(text);
                        draft.moves[slot] = chosen == null ? text : chosen.getName();
                        if (!completingMove) moveQueries[slot] = text;
                        updateMoveDetails(slot, chosen);
                        updateSave();
                    });
            input.setEditable(canEdit() && draft.species != null);
            moveFields[i] = input;
            updateMoveDetails(i, move);
        }
    }

    private void updateMoveDetails(int slot, MoveTemplate move) {
        int y = contentY + slot * moveStep + (Math.min(WIDGET_HEIGHT, moveStep - 2) - 9) / 2;
        moveIcons[slot] = move == null ? null : new TypeIcon(contentX + 39, y, move.getElementalType(),
                null, false, true, 0f, 0f, 1f);
        var card = move == null ? null : new MoveDetailsCard(move, draft.original, textRenderer, width, height);
        moveCards[slot] = card;
        moveFields[slot].setTooltip(Tooltip.of(MoveDetailsCard.BROWSE_HINT,
                card == null ? MoveDetailsCard.BROWSE_HINT : card.narration()));
    }

    private int hoveredMove(double mouseX, double mouseY) {
        if (tab == Tab.MOVES && mouseX >= contentX && mouseX < contentX + contentWidth) {
            for (int i = 0; i < moveFields.length; i++) {
                if (mouseY >= moveFields[i].getY() && mouseY < moveFields[i].getBottom()) return i;
            }
        }
        return -1;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        int slot = hoveredMove(mouseX, mouseY);
        if (slot >= 0 && vertical != 0 && moveCards[slot] != null && moveCards[slot].scroll(vertical < 0 ? 1 : -1)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    private boolean cycleMove(int slot, int step) {
        String prefix = PartyDraft.normalizeMove(moveQueries[slot]);
        if (prefix.isEmpty()) return false; // Keep normal keyboard navigation until the user types.
        var matches = moveCatalog.stream().filter(move -> PartyDraft.normalizeMove(move.getName()).startsWith(prefix)
                || PartyDraft.normalizeMove(move.getDisplayName().getString()).startsWith(prefix)).toList();
        if (matches.isEmpty()) return false;
        var current = PartyDraft.findMove(draft.moves[slot]);
        int index = matches.indexOf(current);
        var next = matches.get(index < 0 ? (step > 0 ? 0 : matches.size() - 1)
                : Math.floorMod(index + step, matches.size()));
        completingMove = true;
        try {
            moveFields[slot].setText(next.getDisplayName().getString());
        } finally {
            completingMove = false;
        }
        return true;
    }

    private int natureHeight() {
        return formHeight < 100 ? 14 : WIDGET_HEIGHT;
    }

    private void initNatureSelector() {
        int x = contentX + statLabelWidth();
        int right = contentX + contentWidth;
        int arrowWidth = 18;
        boolean enabled = canEdit() && draft.species != null && !natures.isEmpty();
        var previous = button(x, contentY, arrowWidth, natureHeight(), Text.literal("‹"), Style.QUIET, () -> cycleNature(-1));
        previous.setTooltip(Tooltip.of(Text.literal("Previous nature")));
        previous.active = enabled;
        natureButton = button(x + arrowWidth, contentY, right - x - 2 * arrowWidth, natureHeight(),
                natureButtonLabel(), Style.ROW, this::chooseNature);
        natureButton.setTooltip(Tooltip.of(Text.literal("Search natures")));
        natureButton.active = enabled;
        var next = button(right - arrowWidth, contentY, arrowWidth, natureHeight(), Text.literal("›"), Style.QUIET, () -> cycleNature(1));
        next.setTooltip(Tooltip.of(Text.literal("Next nature")));
        next.active = enabled;
    }

    private Text natureButtonLabel() {
        return Text.literal((draft.nature == null ? "Choose nature" : natureName(draft.nature)) + "  ▾");
    }

    ButtonWidget natureSelector() {
        return natureButton;
    }

    private void cycleNature(int step) {
        int current = natures.indexOf(draft.nature);
        int next = current < 0 ? (step > 0 ? 0 : natures.size() - 1) : Math.floorMod(current + step, natures.size());
        selectNature(natures.get(next));
    }

    private void selectNature(Nature nature) {
        draft.nature = nature;
        status = "";
        natureButton.setMessage(natureButtonLabel());
        updateSave();
    }

    private void chooseNature() {
        var choices = natures.stream().map(nature -> {
            String name = natureName(nature);
            var label = Text.literal(name).append(natureEffects(nature));
            return new PartyChoiceScreen.Choice(label.getString() + " " + nature.getName(),
                    label, nature == draft.nature, () -> selectNature(nature));
        }).toList();
        client.setScreen(new PartyChoiceScreen(this, choices));
    }

    private static String natureName(Nature nature) {
        return Text.translatable(nature.getDisplayName()).getString();
    }

    private static Text natureEffects(Nature nature) {
        var text = Text.empty();
        var increased = nature.getIncreasedStat();
        var decreased = nature.getDecreasedStat();
        if (increased != null && !increased.equals(decreased)) {
            text.append(Text.literal("  +" + StatIds.displayName(increased).getString()).withColor(GREEN));
        }
        if (decreased != null && !decreased.equals(increased)) {
            text.append(Text.literal("  −" + StatIds.displayName(decreased).getString()).withColor(RED));
        }
        return text;
    }

    private void cycleSpecies(int direction) {
        if (!canEdit() || speciesField == null) {
            return;
        }
        String query = normalizeSpecies(speciesQuery);
        List<Species> matches = allSpecies.stream().filter(species -> matchesSpecies(species, query)).toList();
        if (matches.isEmpty()) {
            return;
        }
        int current = matches.indexOf(draft.species);
        int index = current == -1 && direction < 0 ? matches.size() - 1
                : Math.floorMod(current + direction, matches.size());
        draft.species = matches.get(index);
        completingSpecies = true;
        speciesField.setText(draft.species.getTranslatedName().getString());
        completingSpecies = false;
        setFocused(speciesField);
        updateSave();
    }

    private Species findSpecies(String text) {
        String name = normalizeSpecies(text);
        return allSpecies.stream().filter(species -> name.equals(normalizeSpecies(species.getTranslatedName().getString()))
                || name.equals(normalizeSpecies(species.getName()))
                || name.equals(normalizeSpecies(species.getResourceIdentifier().getPath()))
                || name.equals(normalizeSpecies(species.getResourceIdentifier().toString()))).findFirst().orElse(null);
    }

    private static boolean matchesSpecies(Species species, String query) {
        return normalizeSpecies(species.getTranslatedName().getString()).contains(query)
                || normalizeSpecies(species.getName()).contains(query)
                || normalizeSpecies(species.getResourceIdentifier().toString()).contains(query);
    }

    private static String normalizeSpecies(String query) {
        return query.strip().toLowerCase(Locale.ROOT).replace(" ", "");
    }

    private ButtonWidget speciesArrow(int x, String label, int direction, String hint) {
        var arrow = button(x, speciesY(), 17, WIDGET_HEIGHT, Text.literal(label), Style.QUIET,
                () -> cycleSpecies(direction));
        arrow.active = canEdit();
        arrow.setTooltip(Tooltip.of(Text.literal(hint)));
        return arrow;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (handled && speciesField != null && (getFocused() == previousSpecies || getFocused() == nextSpecies)
                && getFocused() != null) {
            setFocused(speciesField);
        }
        return handled;
    }

    private TextFieldWidget field(int x, int y, int w, int h, String label, String value, String placeholder,
                                  int maxLength, Consumer<String> changed) {
        var field = input(x, y, w, h, label);
        field.setMaxLength(maxLength);
        field.setText(value);
        field.setPlaceholder(Text.literal(placeholder).withColor(0xCEDCDD));
        field.setEditable(canEdit());
        field.setChangedListener(valueText -> {
            if (canEdit()) status = "";
            changed.accept(valueText);
        });
        return field;
    }

    private TextFieldWidget numberField(int x, int y, int w, int h, String label,
                                        String[] values, int row, String placeholder, int maxLength) {
        boolean hasCurrent = !placeholder.equals("Auto");
        String initial = values[row].isEmpty() && hasCurrent ? placeholder : values[row];
        var field = field(x, y, w, h, label, initial, placeholder, maxLength, text -> {
            // Display real editable digits, but submit only explicit changes, not displayed defaults.
            values[row] = text.equals(placeholder) ? "" : text;
            updateSave();
        });
        field.setTextPredicate(text -> text.chars().allMatch(value -> value >= '0' && value <= '9'));
        field.setEditable(canEdit() && draft.species != null);
        return field;
    }

    private boolean canEdit() {
        return !pending && !timedOut && !snapshot.terminal();
    }

    private void select(int slot) {
        if (slot == selected) {
            return;
        }
        if (draft.isDirty()) {
            status = "Save or reset this draft before changing slots.";
            return;
        }
        selected = slot;
        statPage = 0;
        status = "";
        tab = Tab.POKEMON;
        resetDraft();
        clearAndInit();
    }

    private void selectTab(Tab target) {
        tab = target;
        clearAndInit();
    }

    private void resetDraft() {
        draft = new PartyDraft(snapshot.party()[selected]);
        speciesQuery = "";
    }

    private void updateSave() {
        dirty = draft.isDirty();
        validation = dirty ? draft.validate(snapshot.minimum(), snapshot.maximum()) : "";
        for (Tab target : Tab.values()) {
            var control = tabs[target.ordinal()];
            boolean needsSpecies = target != Tab.POKEMON && draft.species == null;
            control.active = !pending && target != tab && !needsSpecies;
            if (control instanceof SectionTab section) section.showHint(needsSpecies);
        }
        if (save != null) {
            save.active = canEdit() && dirty && validation.isEmpty();
            reset.active = canEdit() && dirty;
        }
    }

    private void submit() {
        if (!ClientPlayNetworking.canSend(SaveParty.ID)) {
            status = "The server cannot receive PokéEdit changes.";
            return;
        }
        ClientPlayNetworking.send(draft.packet(snapshot, selected));
        pending = true;
        sentAt = Util.getMeasuringTimeMs();
        status = "Waiting for the server…";
        clearAndInit();
    }

    @Override
    public void tick() {
        if (pending && Util.getMeasuringTimeMs() - sentAt > 8000) {
            pending = false;
            timedOut = true;
            status = "No reply. Reopen PokéEdit to check whether the changes were saved.";
            clearAndInit();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (tab == Tab.MOVES && displayedMove >= 0 && moveCards[displayedMove] != null
                && (keyCode == GLFW.GLFW_KEY_PAGE_UP || keyCode == GLFW.GLFW_KEY_PAGE_DOWN)
                && moveCards[displayedMove].scroll(keyCode == GLFW.GLFW_KEY_PAGE_UP ? -1 : 1)) return true;
        if (hasControlDown() && keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_3) {
            Tab target = Tab.values()[keyCode - GLFW.GLFW_KEY_1];
            if (tabs[target.ordinal()].active) selectTab(target);
            return true;
        }
        if (tab == Tab.STATS && getFocused() == natureButton && natureButton.active
                && (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_RIGHT)) {
            cycleNature(keyCode == GLFW.GLFW_KEY_LEFT ? -1 : 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB && tab == Tab.MOVES && canEdit()) {
            for (int i = 0; i < moveFields.length; i++) {
                if (getFocused() == moveFields[i] && cycleMove(i, (modifiers & GLFW.GLFW_MOD_SHIFT) == 0 ? 1 : -1)) return true;
            }
        }
        if (keyCode == GLFW.GLFW_KEY_S && hasControlDown()) {
            if (save != null && save.active) submit();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB && speciesField != null && speciesField.isFocused() && canEdit()) {
            cycleSpecies((modifiers & GLFW.GLFW_MOD_SHIFT) == 0 ? 1 : -1);
            return true;
        }
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                && speciesField != null && speciesField.isFocused() && canEdit()) {
            setFocused(nicknameField);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        applyBlur(delta);
        int right = left + panelWidth;
        context.drawTexture(EDITOR_ICON, left, top + 1, 0, 0, EDITOR_ICON_SIZE, EDITOR_ICON_SIZE,
                EDITOR_ICON_SIZE, EDITOR_ICON_SIZE);
        heading(context, left + 19, top + 2);
        String owner = textRenderer.trimToWidth(snapshot.targetName() + "’s party", panelWidth - 115);
        context.drawTextWithShadow(textRenderer, owner, right - 26 - textRenderer.getWidth(owner), top + 5, SECONDARY_COLOR);
        int headerLine = Math.min(top + 21, tabY - 2);
        context.fill(left, headerLine, right, headerLine + 1, RULE);
        context.fill(left, headerLine, left + 13, headerLine + 1, ACCENT_EDGE);
        context.fill(left, footerY - (stackedFooter ? 25 : 5), right, footerY - (stackedFooter ? 24 : 4), RULE);
        if (!partyStrip) {
            var name = Text.literal(draftName()).styled(style -> style.withBold(true));
            var fitted = textRenderer.trimToWidth(name, sideWidth - 4);
            context.drawTextWithShadow(textRenderer, Language.getInstance().reorder(fitted),
                    left + (sideWidth - textRenderer.getWidth(fitted)) / 2, gridY - (previewTypeIcon == null ? 13 : 26), TEXT_COLOR);
            if (previewTypeIcon != null) {
                previewTypeIcon.render(context);
                context.drawTextWithShadow(textRenderer, previewTypes, typeTextX, gridY - 12, SECONDARY_COLOR);
            }
            if (!portraitInSlot && portrait == null && gridY - (top + 26) >= 45) {
                int middleY = (top + 26 + gridY - 18) / 2;
                drawBall(context, left + sideWidth / 2 - 13, middleY - 13, 2);
            }
        }

        switch (tab) {
            case POKEMON -> {
                int labelOffset = inlineIdentity() ? 5 : -12;
                label(context, "Species", contentX, speciesY() + labelOffset, contentWidth, TEXT_COLOR);
                label(context, "Nickname", contentX, nicknameY() + labelOffset, contentWidth, TEXT_COLOR);
                String hint = "Tab to browse · Enter for nickname";
                if (textRenderer.getWidth(hint) > contentWidth) hint = "Tab to browse";
                int hintY = nicknameY() + WIDGET_HEIGHT + 4;
                if (speciesField.isFocused() && hintY + textRenderer.fontHeight <= contentY + formHeight) {
                    label(context, hint, contentX, hintY, contentWidth, SECONDARY_COLOR);
                }
                if (draft.original == null && !speciesField.isFocused() && formHeight >= 104) {
                    String addHint = draft.species == null ? "Choose a species to fill this slot." : "Save to add this Pokémon at level 1.";
                    wrappedLabel(context, Text.literal(addHint), contentY + 87, SECONDARY_COLOR);
                }
            }
            case STATS -> {
                int columnsX = contentX + statLabelWidth();
                int columnWidth = statColumnWidth();
                label(context, "Nature", contentX, contentY + (natureHeight() - 8) / 2, statLabelWidth(), TEXT_COLOR);
                label(context, "Total", columnsX, statHeaderY, columnWidth, TEXT_COLOR);
                label(context, "IV", columnsX + columnWidth, statHeaderY, columnWidth, TEXT_COLOR);
                label(context, "EV", columnsX + columnWidth * 2, statHeaderY, columnWidth, TEXT_COLOR);
                int first = statPage * statRows;
                for (int i = first; i < Math.min(first + statRows, PartyDraft.STAT_COUNT); i++) {
                    int fieldY = statHeaderY + 12 + (i - first) * statRowHeight;
                    int labelY = fieldY + (Math.min(WIDGET_HEIGHT, statRowHeight - 1) - 8) / 2;
                    int effect = StatMath.natureModifier(draft.nature, StatIds.EDITABLE.get(i));
                    int color = effect > 0 ? GREEN : effect < 0 ? RED : SECONDARY_COLOR;
                    label(context, STAT_LABELS[i], contentX, labelY, statLabelWidth() - 10, color);
                    if (effect != 0) {
                        context.drawTextWithShadow(textRenderer, effect > 0 ? "+" : "−", columnsX - 9, labelY, color);
                    }
                }
            }
            case MOVES -> {
                for (int i = 0; i < PartyDraft.MOVE_COUNT; i++) {
                    label(context, "Move " + (i + 1), contentX, contentY + i * moveStep + (Math.min(WIDGET_HEIGHT, moveStep - 2) - 8) / 2, 38, TEXT_COLOR);
                    if (moveIcons[i] != null) moveIcons[i].render(context);
                }
                if (draft.original == null && !draft.movesChanged() && formHeight >= 111) {
                    label(context, "Blank uses starting moves.", contentX, contentY + 99, contentWidth, SECONDARY_COLOR);
                }
            }
        }
        String feedback = !validation.isEmpty() ? validation : !status.isEmpty() ? status : dirty ? "Unsaved changes" : "";
        int color = !validation.isEmpty() ? RED : dirty ? 0xFFE2A3 : TEXT_COLOR;
        int feedbackWidth = stackedFooter ? panelWidth - 2 : actionX - left - 8;
        var lines = textRenderer.wrapLines(Text.literal(feedback), Math.max(1, feedbackWidth));
        int lineCount = Math.min(2, lines.size());
        int y = (stackedFooter ? footerY - 21 : footerY) + (WIDGET_HEIGHT - lineCount * textRenderer.fontHeight) / 2;
        for (int i = 0; i < lineCount; i++) {
            int lineY = y + i * textRenderer.fontHeight;
            if (i == 1 && lines.size() > 2) {
                context.drawTextWithShadow(textRenderer, "More…", left + 1, lineY, color);
            } else {
                context.drawTextWithShadow(textRenderer, lines.get(i), left + 1, lineY, color);
            }
        }
        if (lines.size() > 2 && mouseX >= left && mouseX < left + feedbackWidth
                && mouseY >= y && mouseY < y + lineCount * textRenderer.fontHeight) {
            setTooltip(textRenderer.wrapLines(Text.literal(feedback), Math.min(panelWidth - 12, 280)));
        }
    }

    private void wrappedLabel(DrawContext context, Text text, int y, int color) {
        for (var line : textRenderer.wrapLines(text, contentWidth)) {
            context.drawTextWithShadow(textRenderer, line, contentX, y, color);
            y += textRenderer.fontHeight;
        }
    }

    /** Marks the empty Pokémon preview. */
    private static void drawBall(DrawContext ctx, int x, int y, int scale) {
        var matrices = ctx.getMatrices();
        matrices.push();
        matrices.translate(x, y, 0);
        matrices.scale(scale, scale, 1);
        int ink = 0xFF40324E;
        ctx.fill(4, 0, 9, 13, ink);
        ctx.fill(2, 1, 11, 12, ink);
        ctx.fill(1, 2, 12, 11, ink);
        ctx.fill(0, 4, 13, 9, ink);
        ctx.fill(4, 1, 9, 2, ACCENT_EDGE);
        ctx.fill(2, 2, 11, 5, ACCENT_EDGE);
        ctx.fill(1, 4, 12, 6, ACCENT_EDGE);
        ctx.fill(1, 7, 12, 9, 0xFFF2E8ED);
        ctx.fill(2, 9, 11, 11, 0xFFF2E8ED);
        ctx.fill(4, 11, 9, 12, 0xFFF2E8ED);
        ctx.fill(2, 3, 4, 5, 0xFFEC94BB);
        ctx.fill(9, 3, 11, 5, 0xFFEC94BB);
        ctx.fill(4, 4, 9, 9, ink);
        ctx.fill(5, 5, 8, 8, 0xFFFAFFFF);
        matrices.pop();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        if (portrait != null) portrait.render(context, mouseX, mouseY, delta);
        for (var preview : previews) {
            if (preview != null) preview.render(context, mouseX, mouseY, delta);
        }
        if (tab == Tab.MOVES) {
            int slot = hoveredMove(mouseX, mouseY);
            int anchorX = mouseX;
            if (slot < 0 && client.getNavigationType().isKeyboard()) {
                for (int i = 0; i < moveFields.length; i++) {
                    if (moveFields[i].isFocused()) {
                        slot = i;
                        anchorX = moveFields[i].getX();
                        break;
                    }
                }
            }
            displayedMove = slot;
            if (slot >= 0) {
                if (moveCards[slot] == null) setTooltip(MoveDetailsCard.BROWSE_HINT);
                else {
                    clearTooltip();
                    moveCards[slot].draw(context, moveFields[slot], anchorX, width, height);
                }
            }
        }
    }

    private int statLabelWidth() {
        return Math.clamp(contentWidth / 4, 52, 70);
    }

    private int statColumnWidth() {
        return (contentWidth - statLabelWidth()) / 3;
    }

    @Override
    public void close() {
        if (pending) {
            return;
        }
        if (!timedOut && draft.isDirty()) {
            client.setScreen(new ConfirmScreen(
                    discard -> client.setScreen(discard ? null : this),
                    Text.literal("Discard changes to this slot?"), Text.literal("Save applies changes to the party.")));
        } else {
            super.close();
        }
    }

    private final class PartySlotButton extends ButtonWidget {
        private final int slot;

        private PartySlotButton(int slot, int x, int y, int w, Text label) {
            super(x, y, w, gridRowHeight - 2, label, b -> select(slot), DEFAULT_NARRATION_SUPPLIER);
            this.slot = slot;
        }

        @Override
        protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            boolean chosen = selected == slot;
            boolean hover = active && isSelected();
            if (chosen || hover) {
                cutCorners(context, getX(), getY(), getRight(), getBottom(), chosen ? 0x594B365F : 0x45221F2B);
                context.fill(getX() + 2, getBottom() - 1, getRight() - 2, getBottom(), chosen ? ACCENT_EDGE : RULE);
                context.fill(getX(), getY() + 2, getX() + 1, getY() + 7, ACCENT_EDGE);
                context.fill(getX() + 1, getY() + 1, getX() + 6, getY() + 2, ACCENT_EDGE);
            }
            boolean showDraft = portraitInSlot && chosen;
            if (showDraft ? draft.species == null : snapshot.party()[slot] == null) {
                int cx = getX() + getWidth() / 2;
                int cy = getY() + getHeight() / 2;
                int color = chosen || hover ? ACCENT_EDGE : 0xFFCFDEE0;
                context.fill(cx - 4, cy - 1, cx + 5, cy + 2, 0x88304449);
                context.fill(cx - 1, cy - 4, cx + 2, cy + 5, 0x88304449);
                context.fill(cx - 3, cy, cx + 4, cy + 1, color);
                context.fill(cx, cy - 3, cx + 1, cy + 4, color);
            } else {
                Text name = showDraft ? Text.literal(draftName()) : getMessage();
                drawScrollableText(context, textRenderer, name, getX() + 1, getBottom() - 13,
                        getRight() - 1, getBottom() - 2, chosen || hover ? ACCENT : TEXT_COLOR);
            }
        }
    }

    private final class SectionTab extends ButtonWidget {
        private final Tab target;
        private final Tooltip shortcut;
        private final Tooltip needsSpecies = Tooltip.of(Text.literal("Choose a species first"));

        SectionTab(Tab target, int x, int width) {
            super(x, tabY, width - 2, tabHeight, Text.literal(target.label), b -> selectTab(target), DEFAULT_NARRATION_SUPPLIER);
            this.target = target;
            shortcut = Tooltip.of(Text.literal(target.label + " (Ctrl+" + (target.ordinal() + 1) + ")"));
        }

        void showHint(boolean chooseSpecies) {
            setTooltip(chooseSpecies ? needsSpecies : shortcut);
        }

        @Override
        protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
            boolean current = target == tab;
            boolean hover = active && isSelected();
            int color = current ? ACCENT_INK : active ? TEXT_COLOR : SECONDARY_COLOR;
            cutCorners(ctx, getX(), getY() + 1, getRight(), getBottom() - 1,
                    current ? ACCENT_EDGE : hover ? 0xB24B3C5C : 0x78221F2B);
            if (hover) ctx.fill(getX() + 2, getBottom() - 2, getRight() - 2, getBottom() - 1, ACCENT_EDGE);
            int textWidth = textRenderer.getWidth(getMessage());
            boolean icon = textWidth + 16 <= getWidth();
            int x = getX() + Math.max(1, (getWidth() - textWidth - (icon ? 12 : 0)) / 2);
            int y = getY() + (getHeight() - 8) / 2;
            if (icon) {
                target.drawIcon(ctx, x, y, 0xFF000000 | color);
                x += 12;
            }
            ctx.drawText(textRenderer, textRenderer.trimToWidth(target.label, getRight() - x - 1), x, y, color, !current);
        }
    }

    private enum Tab {
        POKEMON("Pokémon"), STATS("Stats"), MOVES("Moves");

        final String label;

        void drawIcon(DrawContext ctx, int x, int y, int color) {
            switch (this) {
                case POKEMON -> {
                    ctx.fill(x + 2, y, x + 6, y + 1, color);
                    ctx.fill(x + 1, y + 1, x + 7, y + 3, color);
                    ctx.fill(x, y + 3, x + 3, y + 4, color);
                    ctx.fill(x + 5, y + 3, x + 8, y + 4, color);
                    ctx.fill(x, y + 4, x + 1, y + 6, color);
                    ctx.fill(x + 7, y + 4, x + 8, y + 6, color);
                    ctx.fill(x + 1, y + 6, x + 7, y + 7, color);
                }
                case STATS -> {
                    ctx.fill(x, y + 4, x + 2, y + 7, color);
                    ctx.fill(x + 3, y + 2, x + 5, y + 7, color);
                    ctx.fill(x + 6, y, x + 8, y + 7, color);
                }
                case MOVES -> {
                    ctx.fill(x + 3, y, x + 7, y + 2, color);
                    ctx.fill(x + 2, y + 2, x + 5, y + 3, color);
                    ctx.fill(x + 1, y + 3, x + 7, y + 4, color);
                    ctx.fill(x + 3, y + 4, x + 6, y + 6, color);
                    ctx.fill(x + 2, y + 6, x + 4, y + 7, color);
                }
            }
        }

        Tab(String label) {
            this.label = label;
        }
    }
}
