package dev.tee.stateditor.data;

import com.cobblemon.mod.common.api.storage.PokemonStore;
import com.cobblemon.mod.common.api.storage.party.PartyStore;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobblemon.mod.common.api.storage.pc.link.PCLinkManager;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.util.PlayerExtensionsKt;
import dev.tee.stateditor.StatEditorMod;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server side lookup of a Pokémon that belongs to (or is visible to) a player.
 */
public final class PokemonLookup {
    /** Entity id that means "no entity, use the party or the PC". */
    public static final int NO_ENTITY = -1;

    private PokemonLookup() {
    }

    /** @return the party store of a player. */
    public static PartyStore party(ServerPlayerEntity player) {
        return PlayerExtensionsKt.party(player);
    }

    /** @return the PC store of a player, or {@code null} when they have none. */
    public static PCStore pc(ServerPlayerEntity player) {
        return PCLinkManager.INSTANCE.getPC(player);
    }

    /**
     * Finds the Pokémon a request refers to.
     *
     * @param server   the server, used to look up entities
     * @param player   the player that sent the request
     * @param pokemonId the Pokémon in the party or the PC
     * @param entityId the id of a Pokémon entity in the world, or {@code -1}
     * @return the Pokémon, or {@code null} when it cannot be found
     */
    public static Pokemon find(MinecraftServer server, ServerPlayerEntity player, UUID pokemonId, int entityId) {
        if (entityId != NO_ENTITY) {
            for (var world : server.getWorlds()) {
                Entity entity = world.getEntityById(entityId);
                if (entity instanceof PokemonEntity pokemonEntity) {
                    return pokemonEntity.getPokemon();
                }
            }
            return null;
        }
        return find(player, pokemonId);
    }

    /**
     * Finds a Pokémon by its UUID in the player's party or PC.
     *
     * @return the Pokémon, or {@code null} when it cannot be found
     */
    public static Pokemon find(ServerPlayerEntity player, UUID pokemonId) {
        if (pokemonId == null) {
            return null;
        }
        PartyStore party = party(player);
        if (party != null) {
            Pokemon pokemon = party.get(pokemonId);
            if (pokemon != null) {
                return pokemon;
            }
        }
        PCStore pc = pc(player);
        if (pc != null) {
            Pokemon pokemon = pc.get(pokemonId);
            if (pokemon != null) {
                return pokemon;
            }
        }
        return null;
    }

    /** @return {@code true} when the Pokémon is stored in the party or the PC of the player. */
    public static boolean isOwn(ServerPlayerEntity player, Pokemon pokemon) {
        if (pokemon == null) {
            return false;
        }
        PartyStore party = party(player);
        if (party != null && party.get(pokemon.getUuid()) != null) {
            return true;
        }
        PCStore pc = pc(player);
        return pc != null && pc.get(pokemon.getUuid()) != null;
    }

    /**
     * @return every player that currently observes the store the Pokémon lives in. Those players
     * get their own copy of the Pokémon and therefore have to be told about stat changes.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static List<ServerPlayerEntity> observers(Pokemon pokemon) {
        List<ServerPlayerEntity> result = new ArrayList<>();
        if (pokemon == null) {
            return result;
        }
        try {
            var observable = pokemon.getStoreCoordinates();
            if (observable == null) {
                return result;
            }
            var coordinates = observable.get();
            if (coordinates == null) {
                return result;
            }
            PokemonStore store = coordinates.getStore();
            if (store == null) {
                return result;
            }
            for (Object player : store.getObservingPlayers()) {
                if (player instanceof ServerPlayerEntity serverPlayer) {
                    result.add(serverPlayer);
                }
            }
        } catch (Throwable throwable) {
            StatEditorMod.LOGGER.warn("Could not resolve the observers of a Pokémon", throwable);
        }
        return result;
    }
}
