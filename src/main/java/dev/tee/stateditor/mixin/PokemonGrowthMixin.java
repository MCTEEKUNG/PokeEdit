package dev.tee.stateditor.mixin;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.net.messages.client.PokemonUpdatePacket;
import com.cobblemon.mod.common.net.messages.client.pokemon.update.EVsUpdatePacket;
import com.cobblemon.mod.common.net.messages.client.pokemon.update.IVsUpdatePacket;
import com.cobblemon.mod.common.util.codec.internal.PokemonP1;
import com.cobblemon.mod.common.util.codec.internal.PokemonP2;
import com.cobblemon.mod.common.util.codec.internal.PokemonP3;
import com.cobblemon.mod.common.util.codec.internal.ClientPokemonP1;
import com.cobblemon.mod.common.util.codec.internal.ClientPokemonP2;
import com.cobblemon.mod.common.util.codec.internal.ClientPokemonP3;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.tee.stateditor.data.StatHydration;
import dev.tee.stateditor.battle.StatGrowthSync;
import dev.tee.stateditor.data.StatOverrideStore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Anchor legacy absolute edits before the first growth/evolution after updating the mod. */
@Mixin(Pokemon.class)
public abstract class PokemonGrowthMixin {
    @Inject(method = {"setLevel", "setSpecies", "setForm", "setIV", "setEV", "hyperTrainIV",
            "setNature", "setMintedNature", "setIvs$common", "setEvs$common"}, at = @At("HEAD"), remap = false)
    private void stateditor$migrateBeforeGrowth(CallbackInfo ci) {
        StatOverrideStore.migrateLegacy((Pokemon) (Object) this);
    }

    @Inject(method = {"setLevel", "setSpecies", "setForm", "setIV", "setEV", "hyperTrainIV",
            "setNature", "setMintedNature", "setIvs$common", "setEvs$common"}, at = @At("RETURN"), remap = false)
    private void stateditor$syncAfterGrowth(CallbackInfo ci) {
        StatGrowthSync.changed((Pokemon) (Object) this);
    }

    @Inject(method = "onChange", at = @At("HEAD"), remap = false)
    private void stateditor$syncObservableStats(PokemonUpdatePacket<?> packet, CallbackInfo ci) {
        // EV rewards and direct IV/EV container edits need not pass through Pokemon.setEV/setIV.
        if (packet instanceof EVsUpdatePacket || packet instanceof IVsUpdatePacket) {
            StatGrowthSync.changed((Pokemon) (Object) this);
        }
    }

    @WrapMethod(method = "initialize", remap = false)
    private Pokemon stateditor$initializeFully(Operation<Pokemon> original) {
        Pokemon result = StatHydration.run(() -> original.call());
        StatOverrideStore.migrateLegacy(result);
        return result;
    }

    @WrapMethod(method = "copyFrom", remap = false)
    private Pokemon stateditor$copyFully(Pokemon source, Operation<Pokemon> original) {
        Pokemon result = StatHydration.run(() -> original.call(source));
        StatOverrideStore.migrateLegacy(result);
        return result;
    }

    @WrapMethod(method = "ROOT_CODEC$lambda$0$0", remap = false)
    private static Pokemon stateditor$decodeFully(PokemonP1 first, PokemonP2 second, PokemonP3 third,
                                                  Operation<Pokemon> original) {
        Pokemon result = StatHydration.run(() -> original.call(first, second, third));
        StatOverrideStore.migrateLegacy(result);
        return result;
    }

    @WrapMethod(method = "CLIENT_CODEC$lambda$0$0", remap = false)
    private static Pokemon stateditor$decodeClientFully(ClientPokemonP1 first, ClientPokemonP2 second,
                                                        ClientPokemonP3 third, Operation<Pokemon> original) {
        Pokemon result = StatHydration.run(() -> original.call(first, second, third));
        StatOverrideStore.migrateLegacy(result);
        return result;
    }
}
