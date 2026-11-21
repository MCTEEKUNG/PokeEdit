package dev.tee.stateditor.item;

import dev.tee.stateditor.config.StatEditorConfig;
import dev.tee.stateditor.party.PartyEditor;
import dev.tee.stateditor.permission.StatEditorPermissions;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item.TooltipContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;


/** Opens the holder's party when used without an entity or block hit. */
public final class PokeEditConsoleItem extends Item {
    public PokeEditConsoleItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (user.isSpectator()) {
            return TypedActionResult.pass(user.getStackInHand(hand));
        }
        if (user instanceof ServerPlayerEntity player) {
            PartyEditor.openNearby(player, player);
        }
        return TypedActionResult.success(user.getStackInHand(hand), world.isClient());
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean dirty) {
        if (world.isClient() || !(entity instanceof ServerPlayerEntity player)) {
            return;
        }
        if (!StatEditorConfig.removeItemFromNonCreative) {
            return;
        }
        if (player.getAbilities().creativeMode || StatEditorPermissions.canUseConsole(player)) {
            return;
        }
        removeFromInventory(player);
        player.sendMessage(Text.translatable("stateditor.message.console_removed"), true);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("stateditor.item.pokeedit_console.tooltip_self").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("stateditor.item.pokeedit_console.tooltip_admin").formatted(Formatting.DARK_GRAY));
    }

    private static void removeFromInventory(ServerPlayerEntity player) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.size(); i++) {
            if (inventory.getStack(i).getItem() instanceof PokeEditConsoleItem) {
                inventory.setStack(i, ItemStack.EMPTY);
            }
        }
    }
}
