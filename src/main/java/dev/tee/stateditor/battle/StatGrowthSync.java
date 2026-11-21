package dev.tee.stateditor.battle;

import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.tee.stateditor.data.StatHydration;
import dev.tee.stateditor.data.StatOverrideStore;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Coalesces nested form/level/IV/EV changes before refreshing live simulator snapshots. */
public final class StatGrowthSync {
    private static final Set<Pokemon> PENDING = Collections.newSetFromMap(new IdentityHashMap<>());
    private static volatile MinecraftServer currentServer;

    private StatGrowthSync() {
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            synchronized (PENDING) {
                currentServer = server;
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            synchronized (PENDING) {
                currentServer = null;
                PENDING.clear();
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ArrayList<Pokemon> changed;
            synchronized (PENDING) {
                changed = new ArrayList<>(PENDING);
                PENDING.clear();
            }
            for (Pokemon pokemon : changed) ShowdownOverrides.sync(pokemon, true);
        });
    }

    public static void changed(Pokemon pokemon) {
        if (StatHydration.active() || pokemon.isClient$common()) return;
        synchronized (PENDING) {
            if (currentServer != null && !StatOverrideStore.overrides(pokemon).isEmpty()) PENDING.add(pokemon);
        }
    }

    static void runOnServer(Runnable update) {
        MinecraftServer server = currentServer;
        if (server != null) server.execute(update);
    }
}
