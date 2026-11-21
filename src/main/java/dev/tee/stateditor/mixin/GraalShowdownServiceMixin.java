package dev.tee.stateditor.mixin;

import com.cobblemon.mod.common.battles.runner.graal.GraalShowdownService;
import dev.tee.stateditor.battle.ShowdownOverrides;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GraalShowdownService.class)
public abstract class GraalShowdownServiceMixin {
    @Inject(method = "boot", at = @At("RETURN"), remap = false)
    private void stateditor$installDirectStats(CallbackInfo ci) {
        ShowdownOverrides.install((GraalShowdownService) (Object) this);
    }
}
