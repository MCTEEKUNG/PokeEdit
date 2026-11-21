package dev.tee.stateditor.item;

import dev.tee.stateditor.StatEditorMod;
import dev.tee.stateditor.party.PartyEditor;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;

/** Registers the PokéEdit console and its right-click interactions. */
public final class StatEditorItems {
    private static Item pokeEditConsole;

    private StatEditorItems() {
    }

    /** Registers the mod's items. */
    public static void register() {
        pokeEditConsole = registerConsole("pokeedit_console");
        // Keep existing Stat Console stacks loadable and usable.
        registerConsole("stat_console");
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(pokeEditConsole));

        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (player.isSpectator() || !(player.getStackInHand(hand).getItem() instanceof PokeEditConsoleItem)) {
                return ActionResult.PASS;
            }
            if (player instanceof ServerPlayerEntity editor) {
                ServerPlayerEntity target = entity instanceof ServerPlayerEntity other ? other : editor;
                PartyEditor.openNearby(editor, target);
            }
            return ActionResult.SUCCESS;
        });
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (player.isSpectator() || !(player.getStackInHand(hand).getItem() instanceof PokeEditConsoleItem)) {
                return ActionResult.PASS;
            }
            if (player instanceof ServerPlayerEntity editor) {
                PartyEditor.openNearby(editor, editor);
            }
            return ActionResult.SUCCESS;
        });
    }

    private static Item registerConsole(String path) {
        return Registry.register(Registries.ITEM, Identifier.of(StatEditorMod.MOD_ID, path),
                new PokeEditConsoleItem(new Item.Settings().maxCount(1)));
    }

    public static Item pokeEditConsole() {
        return pokeEditConsole;
    }
}
