package dev.tee.stateditor.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.tee.stateditor.client.PokeEditScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameRenderer.class)
public abstract class PokeEditBlurMixin {
    @ModifyExpressionValue(method = "renderBlur", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/option/GameOptions;getMenuBackgroundBlurrinessValue()I"))
    private int stateditor$increaseEditorBlur(int radius) {
        return MinecraftClient.getInstance().currentScreen instanceof PokeEditScreen ? Math.max(radius, 9) : radius;
    }
}
