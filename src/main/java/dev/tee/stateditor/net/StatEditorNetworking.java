package dev.tee.stateditor.net;

import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.tee.stateditor.StatEditorMod;
import dev.tee.stateditor.battle.ShowdownOverrides;
import dev.tee.stateditor.config.StatEditorConfig;
import dev.tee.stateditor.data.PokemonLookup;
import dev.tee.stateditor.data.StatIds;
import dev.tee.stateditor.data.StatOverrideStore;
import dev.tee.stateditor.party.PartyEditor;
import dev.tee.stateditor.permission.StatEditorPermissions;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Registration of the mod's network channel and the server side handling of stat changes.
 */
public final class StatEditorNetworking {
    private StatEditorNetworking() {
    }

    /** Registers every payload type. Has to happen on the client and on the server. */
    public static void registerPayloads() {
        PayloadTypeRegistry.playC2S().register(SetStatPayload.ID, SetStatPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(OpenPartyPayload.ID, OpenPartyPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SaveParty.ID, SaveParty.CODEC);
        PayloadTypeRegistry.playS2C().register(StatSyncPayload.ID, StatSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenEditorPayload.ID, OpenEditorPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PartySnapshot.ID, PartySnapshot.CODEC);
    }

    /** Registers the server side receiver. */
    public static void registerServerbound() {
        ServerPlayNetworking.registerGlobalReceiver(SetStatPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            handleSetStat(context.server(), player, payload);
        });
        ServerPlayNetworking.registerGlobalReceiver(OpenPartyPayload.ID, (payload, context) ->
                PartyEditor.open(context.player(), context.player()));
        ServerPlayNetworking.registerGlobalReceiver(SaveParty.ID, (payload, context) ->
                PartyEditor.save(context.player(), payload));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> PartyEditor.disconnect(handler.player));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> PartyEditor.clear());
    }

    private static void handleSetStat(MinecraftServer server, ServerPlayerEntity player, SetStatPayload payload) {
        Stat stat = StatIds.fromKey(payload.statKey());
        if (stat == null) {
            player.sendMessage(Text.translatable("stateditor.message.unknown_stat", payload.statKey()), true);
            return;
        }
        Pokemon pokemon = PokemonLookup.find(server, player, payload.pokemonId(), payload.entityId());
        if (pokemon == null) {
            player.sendMessage(Text.translatable("stateditor.message.no_such_pokemon"), true);
            return;
        }
        boolean own = payload.entityId() == PokemonLookup.NO_ENTITY && PokemonLookup.isOwn(player, pokemon);
        if (own && !StatEditorPermissions.canEditOwn(player)) {
            player.sendMessage(Text.translatable("stateditor.message.no_permission"), true);
            return;
        }
        if (!own && !StatEditorPermissions.canEditOthers(player)) {
            player.sendMessage(Text.translatable("stateditor.message.no_permission"), true);
            return;
        }
        if (!payload.clear() && !StatEditorConfig.isValidValue(payload.value())) {
            player.sendMessage(Text.translatable("stateditor.message.out_of_range",
                    StatEditorConfig.minValue, StatEditorConfig.maxValue), true);
            return;
        }

        if (payload.clear()) {
            StatOverrideStore.clear(pokemon, stat);
        } else {
            StatOverrideStore.set(pokemon, stat, payload.value());
        }
        if (stat == Stats.HP) {
            pokemon.setCurrentHealth(Math.min(pokemon.getCurrentHealth(), pokemon.getStat(Stats.HP)));
        }
        StatEditorMod.LOGGER.info("[stat] {} of {} set to {} by {}",
                StatIds.shortName(stat), pokemon.getSpecies().getName(),
                payload.clear() ? "default" : payload.value(), player.getName().getString());

        broadcast(server, pokemon, payload);
    }

    /** Sends the new stat to everybody that can see the Pokémon. */
    public static void broadcast(MinecraftServer server, Pokemon pokemon, SetStatPayload payload) {
        ShowdownOverrides.sync(pokemon);
        Stat stat = StatIds.fromKey(payload.statKey());
        Integer bonus = stat == null ? null : StatOverrideStore.getBonus(pokemon, stat);
        StatSyncPayload sync = new StatSyncPayload(payload.pokemonId(), payload.entityId(),
                payload.statKey(), bonus == null ? 0 : bonus, payload.clear());
        if (payload.entityId() != PokemonLookup.NO_ENTITY) {
            // a Pokémon that is not in a store - tell everybody, only the ones that can see it
            // will be able to apply the change
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                sendTo(player, sync);
            }
            return;
        }
        Set<ServerPlayerEntity> recipients = new LinkedHashSet<>(PokemonLookup.observers(pokemon));
        for (ServerPlayerEntity observer : recipients) {
            sendTo(observer, sync);
        }
    }

    /** Sends a payload to a single player. */
    public static void sendTo(ServerPlayerEntity player, net.minecraft.network.packet.CustomPayload payload) {
        try {
            ServerPlayNetworking.send(player, payload);
        } catch (Throwable throwable) {
            StatEditorMod.LOGGER.warn("Could not send a stat update", throwable);
        }
    }
}
