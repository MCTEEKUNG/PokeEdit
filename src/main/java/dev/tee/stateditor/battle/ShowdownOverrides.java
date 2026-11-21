package dev.tee.stateditor.battle;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.battles.runner.graal.GraalShowdownService;
import com.cobblemon.mod.common.battles.runner.ShowdownService;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.net.messages.client.battle.BattleHealthChangePacket;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;
import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.tee.stateditor.data.StatIds;
import dev.tee.stateditor.data.StatOverrideStore;
import dev.tee.stateditor.data.StatMath;
import kotlin.Unit;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Installs the direct-stat bridge and synchronizes edits through Cobblemon's Showdown dispatcher. */
public final class ShowdownOverrides {
    private ShowdownOverrides() {
    }

    public static int[] values(Pokemon pokemon) {
        int[] values = new int[6];
        for (int i = 0; i < values.length; i++) {
            Integer override = StatOverrideStore.get(pokemon, StatIds.EDITABLE.get(i));
            values[i] = override == null ? 0 : override;
        }
        return values;
    }

    public static void install(GraalShowdownService service) {
        try (var input = ShowdownOverrides.class.getResourceAsStream(
                "/data/stateditor/showdown/stat-overrides.js")) {
            if (input == null) throw new IllegalStateException("Missing Showdown stat bridge");
            service.getContext().eval("js", new String(input.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public static void requireSupportedService() {
        if (!(ShowdownService.Companion.getService() instanceof GraalShowdownService)) {
            throw new IllegalStateException("Stat Editor direct battle stats require Cobblemon's default Graal Showdown service; external socket servers need the same stat bridge installed.");
        }
    }

    public static void sync(Pokemon pokemon) {
        sync(pokemon, false);
    }

    public static void sync(Pokemon pokemon, boolean naturalGrowth) {
        UUID pokemonId = pokemon.getUuid();
        String uuid = pokemonId.toString();
        int[] overrides = values(pokemon);
        String csv = PackedStatCodec.encode(overrides);
        int[] natural = StatIds.EDITABLE.stream().mapToInt(stat -> StatMath.naturalStat(pokemon, stat)).toArray();
        String naturalCsv = PackedStatCodec.encode(natural);
        String sourceSpecies = pokemon.showdownId();
        Cobblemon.INSTANCE.getShowdownThread().queue(service -> {
            if (service instanceof GraalShowdownService graal) {
                var result = graal.getContext().getBindings("js").getMember("stateditorUpdate")
                        .execute(uuid, csv, naturalGrowth, naturalCsv, sourceSpecies);
                JsonArray updates = JsonParser.parseString(result.asString()).getAsJsonArray();
                StatGrowthSync.runOnServer(() -> {
                    for (var entry : updates) {
                        var update = entry.getAsJsonObject();
                        var battle = BattleRegistry.getBattle(UUID.fromString(update.get("battleId").getAsString()));
                        if (battle == null || battle.getEnded()) continue;
                        var actor = battle.getActor(update.get("actorId").getAsString());
                        if (actor == null) continue;
                        var battlePokemon = actor.getPokemonList().stream()
                                .filter(member -> member.getUuid().equals(pokemonId)).findFirst().orElse(null);
                        if (battlePokemon == null) continue;
                        var effected = battlePokemon.getEffectedPokemon();
                        mirrorStats(pokemon, effected, overrides, natural);
                        if (!update.get("hpChanged").getAsBoolean()) continue;
                        float hp = update.get("hp").getAsFloat();
                        float maxhp = update.get("maxhp").getAsFloat();
                        effected.setCurrentHealth((int) hp);
                        if (!update.get("active").getAsBoolean()) continue;
                        for (var active : actor.getActivePokemon()) {
                            if (active.getBattlePokemon() != battlePokemon) continue;
                            battle.sendSidedUpdate(actor,
                                    new BattleHealthChangePacket(active.getPNX(), hp, maxhp),
                                    new BattleHealthChangePacket(active.getPNX(), hp / maxhp, null), false);
                        }
                    }
                });
            }
            return Unit.INSTANCE;
        });
    }

    /** Rebase distinct battle copies without changing their form or the source's permanent bonus. */
    public static void mirrorStats(Pokemon source, Pokemon effected, int[] overrides) {
        mirrorStats(source, effected, overrides, StatIds.EDITABLE.stream()
                .mapToInt(stat -> StatMath.naturalStat(source, stat)).toArray());
    }

    private static void mirrorStats(Pokemon source, Pokemon effected, int[] overrides, int[] natural) {
        if (source == effected) return;
        // Cobblemon.copyFrom can share the persistent compound with the original Pokémon.
        effected.setPersistentData$common(effected.getPersistentData().copy());
        for (int i = 0; i < overrides.length; i++) {
            var stat = StatIds.EDITABLE.get(i);
            if (overrides[i] > 0) StatOverrideStore.set(effected, stat, overrides[i]);
            else if (StatOverrideStore.getBonus(effected, stat) != null) {
                // Reset still needs a local correction when the battle copy has an older level.
                if (StatMath.naturalStat(effected, stat) == natural[i]) StatOverrideStore.clear(effected, stat);
                else StatOverrideStore.set(effected, stat, natural[i]);
            }
        }
    }
}
