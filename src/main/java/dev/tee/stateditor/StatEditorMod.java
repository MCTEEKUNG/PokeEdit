package dev.tee.stateditor;

import dev.tee.stateditor.command.StatCommand;
import dev.tee.stateditor.battle.StatGrowthSync;
import dev.tee.stateditor.config.StatEditorConfig;
import dev.tee.stateditor.item.StatEditorItems;
import dev.tee.stateditor.net.StatEditorNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StatEditorMod implements ModInitializer {
    public static final String MOD_ID = "stateditor";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        StatEditorConfig.load();
        StatEditorItems.register();
        StatEditorNetworking.registerPayloads();
        StatEditorNetworking.registerServerbound();
        StatGrowthSync.register();
        CommandRegistrationCallback.EVENT.register(StatCommand::register);
        LOGGER.info("Cobblemon Stat Editor ready - use /stat or press K to open the editor");
    }
}
