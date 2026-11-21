package dev.tee.stateditor.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Adds a config button to the mod in ModMenu.
 */
public class StatEditorModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return StatEditorConfigScreen::new;
    }
}
