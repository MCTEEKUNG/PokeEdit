package dev.tee.stateditor.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.tee.stateditor.item.StatEditorItems;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Util;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Uses the vanilla entity outline pass to preview the staff's current player target. */
@Mixin(WorldRenderer.class)
public abstract class StaffTargetOutlineMixin {
    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/MinecraftClient;hasOutline(Lnet/minecraft/entity/Entity;)Z"))
    private boolean stateditor$showStaffOutline(MinecraftClient client, Entity entity, Operation<Boolean> original) {
        return original.call(client, entity) || stateditor$isStaffTarget(client, entity);
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/Entity;getTeamColorValue()I"))
    private int stateditor$staffOutlineColor(Entity entity, Operation<Integer> original) {
        if (!stateditor$isStaffTarget(MinecraftClient.getInstance(), entity)) {
            return original.call(entity);
        }
        float hue = (Util.getMeasuringTimeMs() % 1800) / 1800f;
        return MathHelper.hsvToRgb(hue, 0.82f, 1f);
    }

    @Unique
    private static boolean stateditor$isStaffTarget(MinecraftClient client, Entity entity) {
        if (client.currentScreen != null || !(client.crosshairTarget instanceof EntityHitResult hit)
                || hit.getEntity() != entity || !(entity instanceof PlayerEntity target)) {
            return false;
        }
        var player = client.player;
        var staff = StatEditorItems.pokeEditConsole();
        return player != null && player != target && !player.isSpectator() && !target.isSpectator()
                && target.isAlive() && !target.isInvisibleTo(player)
                && (player.getMainHandStack().isOf(staff) || player.getOffHandStack().isOf(staff))
                && player.canInteractWithEntity(target, 0) && player.canSee(target);
    }
}
