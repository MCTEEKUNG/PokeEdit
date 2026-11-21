package dev.tee.stateditor.mixin;

import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.stat.CobblemonStatProvider;
import dev.tee.stateditor.data.StatOverrideStore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes every stat read of a Pokémon go through this mod's overrides.
 * <p>
 * {@code Pokemon.getStat(Stat)} delegates to {@code CobblemonStatProvider.getStatForPokemon}, which
 * is the single place where Cobblemon computes a Pokémon's stats. Overriding it here means the
 * vanilla summary screen, the party list, the health bar, Molang queries and the battle engine all
 * see the edited values - on the client as well as on the server.
 */
@Mixin(CobblemonStatProvider.class)
public abstract class CobblemonStatProviderMixin {
    @Inject(method = "getStatForPokemon", at = @At("HEAD"), cancellable = true, remap = false)
    private void stateditor$applyStatOverride(Pokemon pokemon, Stat stat, CallbackInfoReturnable<Integer> cir) {
        Integer override = StatOverrideStore.get(pokemon, stat);
        if (override != null) {
            cir.setReturnValue(override);
        }
    }
}
