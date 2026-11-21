package dev.tee.stateditor.mixin;

import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import dev.tee.stateditor.battle.PackedTeamEditor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Makes the battle engine use the edited stats.
 * <p>
 * Cobblemon delegates all damage calculations to Showdown, which derives a Pokémon's stats from the
 * base stats of its species plus the IVs, EVs, level and nature that are packed into the team
 * string. This mixin appends explicit overrides for the Showdown bridge, leaving IV/EV data intact.
 */
@Mixin(BattleRegistry.class)
public abstract class BattleRegistryMixin {
    @Inject(method = "packTeam", at = @At("RETURN"), cancellable = true, remap = false)
    private void stateditor$applyStatOverrides(List<? extends BattlePokemon> team, CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(PackedTeamEditor.rewrite(cir.getReturnValue(), team));
    }
}
