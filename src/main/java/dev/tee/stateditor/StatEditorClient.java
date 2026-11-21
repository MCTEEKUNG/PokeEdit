package dev.tee.stateditor;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.storage.ClientPC;
import com.cobblemon.mod.common.client.storage.ClientParty;
import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.tee.stateditor.client.PartyEditorScreen;
import dev.tee.stateditor.client.PokeEditStaffRenderer;
import dev.tee.stateditor.client.StatEditorScreen;
import dev.tee.stateditor.data.PokemonLookup;
import dev.tee.stateditor.data.StatIds;
import dev.tee.stateditor.data.StatOverrideStore;
import dev.tee.stateditor.net.OpenEditorPayload;
import dev.tee.stateditor.net.OpenPartyPayload;
import dev.tee.stateditor.net.PartySnapshot;
import dev.tee.stateditor.net.StatSyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.util.UUID;

public class StatEditorClient implements ClientModInitializer {
    /** The key that opens the stat editor. */
    public static KeyBinding openEditorKey;

    @Override
    public void onInitializeClient() {
        PokeEditStaffRenderer.register();
        openEditorKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.stateditor.open_editor", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.stateditor"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openEditorKey.wasPressed()) {
                if (client.player != null && client.currentScreen == null
                        && ClientPlayNetworking.canSend(OpenPartyPayload.ID)) {
                    ClientPlayNetworking.send(new OpenPartyPayload());
                }
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(StatSyncPayload.ID, (payload, context) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            client.execute(() -> applySync(payload));
        });

        ClientPlayNetworking.registerGlobalReceiver(OpenEditorPayload.ID, (payload, context) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            client.execute(() -> client.setScreen(new StatEditorScreen(payload.entityId(), null)));
        });

        ClientPlayNetworking.registerGlobalReceiver(PartySnapshot.ID, (payload, context) -> {
            MinecraftClient client = context.client();
            client.execute(() -> {
                if (payload.open()) {
                    client.setScreen(new PartyEditorScreen(payload));
                } else if (client.currentScreen instanceof PartyEditorScreen party) {
                    party.receive(payload);
                }
            });
        });
    }

    /** Applies a stat change that was made by the server to the local copy of the Pokémon. */
    public static void applySync(StatSyncPayload payload) {
        var stat = StatIds.fromKey(payload.statKey());
        if (stat == null) {
            return;
        }
        Pokemon pokemon = payload.entityId() != PokemonLookup.NO_ENTITY
                ? findEntityPokemon(payload.entityId())
                : findPokemon(payload.pokemonId());
        if (pokemon == null) {
            return;
        }
        if (payload.clear()) {
            StatOverrideStore.clear(pokemon, stat);
        } else {
            StatOverrideStore.setBonus(pokemon, stat, payload.bonus());
        }
        StatEditorScreen.refreshOpenScreen();
    }

    /** @return the Pokémon of a Pokémon entity in the world, or {@code null}. */
    public static Pokemon findEntityPokemon(int entityId) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null) {
                return null;
            }
            var entity = client.world.getEntityById(entityId);
            if (entity instanceof com.cobblemon.mod.common.entity.pokemon.PokemonEntity pokemonEntity) {
                return pokemonEntity.getPokemon();
            }
        } catch (Throwable throwable) {
            StatEditorMod.LOGGER.warn("Could not find a Pokémon entity", throwable);
        }
        return null;
    }

    /** @return the local copy of a Pokémon, searched in the party and the PC. */
    public static Pokemon findPokemon(UUID pokemonId) {
        if (pokemonId == null) {
            return null;
        }
        try {
            var storage = CobblemonClient.INSTANCE.getStorage();
            for (ClientParty party : storage.getPartyStores().values()) {
                Pokemon pokemon = party.findByUUID(pokemonId);
                if (pokemon != null) {
                    return pokemon;
                }
            }
            for (ClientPC pc : storage.getPcStores().values()) {
                Pokemon pokemon = pc.findByUUID(pokemonId);
                if (pokemon != null) {
                    return pokemon;
                }
            }
        } catch (Throwable throwable) {
            StatEditorMod.LOGGER.warn("Could not find a local Pokémon", throwable);
        }
        return null;
    }
}
