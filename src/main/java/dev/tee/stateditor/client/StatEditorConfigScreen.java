package dev.tee.stateditor.client;

import dev.tee.stateditor.config.StatEditorConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.function.Consumer;

/**
 * A small vanilla configuration screen. It is reachable through ModMenu.
 */
public class StatEditorConfigScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget minField;
    private TextFieldWidget maxField;
    private ButtonWidget selfEditButton;
    private ButtonWidget removeItemButton;
    private ButtonWidget naturalValuesButton;

    public StatEditorConfigScreen(Screen parent) {
        super(Text.translatable("stateditor.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = this.width / 2 - 100;
        int y = 50;

        this.minField = new TextFieldWidget(this.textRenderer, x + 120, y, 60, 16, Text.empty());
        this.minField.setMaxLength(9);
        this.minField.setText(String.valueOf(StatEditorConfig.minValue));
        this.addDrawableChild(this.minField);

        y += 24;
        this.maxField = new TextFieldWidget(this.textRenderer, x + 120, y, 60, 16, Text.empty());
        this.maxField.setMaxLength(9);
        this.maxField.setText(String.valueOf(StatEditorConfig.maxValue));
        this.addDrawableChild(this.maxField);

        y += 24;
        this.selfEditButton = addToggle(x + 120, y, StatEditorConfig.allowSelfEdit,
                value -> StatEditorConfig.allowSelfEdit = value);
        y += 24;
        this.removeItemButton = addToggle(x + 120, y, StatEditorConfig.removeItemFromNonCreative,
                value -> StatEditorConfig.removeItemFromNonCreative = value);
        y += 24;
        this.naturalValuesButton = addToggle(x + 120, y, StatEditorConfig.showNaturalValues,
                value -> StatEditorConfig.showNaturalValues = value);

        y += 32;
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("stateditor.config.save"), button -> {
            save();
            close();
        }).dimensions(x, y, 120, 16).build());
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.cancel"), button -> close())
                .dimensions(x + 128, y, 120, 16).build());
    }

    private ButtonWidget addToggle(int x, int y, boolean value, Consumer<Boolean> consumer) {
        return this.addDrawableChild(ButtonWidget.builder(label(value), button -> {
            boolean newValue = !value;
            consumer.accept(newValue);
            button.setMessage(label(newValue));
        }).dimensions(x, y, 60, 16).build());
    }

    private static Text label(boolean value) {
        return Text.translatable(value ? "stateditor.config.on" : "stateditor.config.off");
    }

    private void save() {
        try {
            StatEditorConfig.minValue = Math.max(1, Integer.parseInt(this.minField.getText().trim()));
        } catch (NumberFormatException exception) {
            // keep the old value
        }
        try {
            StatEditorConfig.maxValue = Math.max(StatEditorConfig.minValue,
                    Integer.parseInt(this.maxField.getText().trim()));
        } catch (NumberFormatException exception) {
            // keep the old value
        }
        StatEditorConfig.save();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);
        int x = this.width / 2 - 100;
        int y = 50;
        context.drawTextWithShadow(this.textRenderer, Text.translatable("stateditor.config.min_value"), x, y + 4, 0xFFFFFF);
        y += 24;
        context.drawTextWithShadow(this.textRenderer, Text.translatable("stateditor.config.max_value"), x, y + 4, 0xFFFFFF);
        y += 24;
        context.drawTextWithShadow(this.textRenderer, Text.translatable("stateditor.config.allow_self_edit"), x, y + 4, 0xFFFFFF);
        y += 24;
        context.drawTextWithShadow(this.textRenderer, Text.translatable("stateditor.config.remove_item"), x, y + 4, 0xFFFFFF);
        y += 24;
        context.drawTextWithShadow(this.textRenderer, Text.translatable("stateditor.config.show_natural"), x, y + 4, 0xFFFFFF);
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }
}
