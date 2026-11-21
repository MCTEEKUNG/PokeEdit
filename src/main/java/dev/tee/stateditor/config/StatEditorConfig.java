package dev.tee.stateditor.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.tee.stateditor.StatEditorMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The mod's settings. They are stored in {@code config/stateditor.json} and can be changed from the
 * in game config screen.
 */
public final class StatEditorConfig {
    /** Smallest stat value a player may set. */
    public static int minValue = 1;
    /** Largest stat value a player may set. */
    public static int maxValue = 9999;
    /** Whether players may edit their own Pokémon. */
    public static boolean allowSelfEdit = true;
    /** Whether the console is removed from survival players who cannot use it. */
    public static boolean removeItemFromNonCreative = true;
    /** Whether the editor shows the untouched stat value next to the edited one. */
    public static boolean showNaturalValues = true;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("stateditor.json");

    private StatEditorConfig() {
    }

    public static void load() {
        if (!Files.exists(PATH)) {
            save();
            return;
        }
        try {
            String json = Files.readString(PATH);
            Settings settings = GSON.fromJson(json, Settings.class);
            if (settings != null) {
                minValue = Math.max(1, settings.minValue);
                maxValue = Math.max(minValue, settings.maxValue);
                allowSelfEdit = settings.allowSelfEdit;
                removeItemFromNonCreative = settings.removeItemFromNonCreative;
                showNaturalValues = settings.showNaturalValues;
            }
        } catch (Exception exception) {
            StatEditorMod.LOGGER.error("Could not read the config, falling back to defaults", exception);
        }
    }

    public static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Settings settings = new Settings();
            settings.minValue = minValue;
            settings.maxValue = maxValue;
            settings.allowSelfEdit = allowSelfEdit;
            settings.removeItemFromNonCreative = removeItemFromNonCreative;
            settings.showNaturalValues = showNaturalValues;
            Files.writeString(PATH, GSON.toJson(settings));
        } catch (IOException exception) {
            StatEditorMod.LOGGER.error("Could not write the config", exception);
        }
    }

    /** @return {@code true} when the given value is inside the configured range. */
    public static boolean isValidValue(int value) {
        return value >= minValue && value <= maxValue;
    }

    private static final class Settings {
        int minValue = StatEditorConfig.minValue;
        int maxValue = StatEditorConfig.maxValue;
        boolean allowSelfEdit = StatEditorConfig.allowSelfEdit;
        boolean removeItemFromNonCreative = StatEditorConfig.removeItemFromNonCreative;
        boolean showNaturalValues = StatEditorConfig.showNaturalValues;
    }
}
