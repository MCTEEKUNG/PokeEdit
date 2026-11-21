package dev.tee.stateditor.party;

import com.cobblemon.mod.common.api.storage.party.PartyPosition;
import com.cobblemon.mod.common.api.storage.party.PartyStore;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.net.messages.client.storage.party.SetPartyPokemonPacket;
import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.tee.stateditor.StatEditorMod;
import dev.tee.stateditor.config.StatEditorConfig;
import dev.tee.stateditor.data.PokemonLookup;
import dev.tee.stateditor.data.StatIds;
import dev.tee.stateditor.data.StatOverrideStore;
import dev.tee.stateditor.net.PartySnapshot;
import dev.tee.stateditor.net.SaveParty;
import dev.tee.stateditor.permission.StatEditorPermissions;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Binds edits to the exact player and party revision opened by the server. */
public final class PartyEditor {
    private static final int SLOT_COUNT = 6;
    private static final int MAX_SESSION_TICKS = 12000;
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, Integer> LAST_OPEN = new HashMap<>();

    private PartyEditor() {
    }

    public static void openNearby(ServerPlayerEntity editor, ServerPlayerEntity target) {
        if (editor != target && (editor.getWorld() != target.getWorld()
                || !editor.canInteractWithEntity(target, 0) || !editor.canSee(target))) {
            message(editor, "stateditor.message.party_target_out_of_reach");
            return;
        }
        open(editor, target);
    }

    public static void open(ServerPlayerEntity editor, ServerPlayerEntity target) {
        int now = editor.getServer().getTicks();
        if (now - LAST_OPEN.getOrDefault(editor.getUuid(), -20) < 4) return;
        LAST_OPEN.put(editor.getUuid(), now);
        if (!allowed(editor, target)) {
            message(editor, "stateditor.message.no_permission");
            return;
        }
        if (!online(target)) {
            message(editor, "stateditor.message.party_target_unavailable");
            return;
        }
        if (!ServerPlayNetworking.canSend(editor, PartySnapshot.ID)) {
            message(editor, "stateditor.message.party_client_required");
            return;
        }
        if (inBattle(editor) || inBattle(target)) {
            message(editor, "stateditor.message.party_in_battle");
            return;
        }
        try {
            Session session = new Session(target, now, editor == target ? editor.getName().getString()
                    : target.getName().getString());
            send(editor, session, PokemonLookup.party(target), "", true, false);
            SESSIONS.put(editor.getUuid(), session);
        } catch (RuntimeException exception) {
            StatEditorMod.LOGGER.error("Could not open the party of {}", target.getUuid(), exception);
            message(editor, "stateditor.message.party_load_failed");
        }
    }

    public static void save(ServerPlayerEntity editor, SaveParty request) {
        Session session = SESSIONS.get(editor.getUuid());
        if (session == null || !session.token.equals(request.token())) return;
        int now = editor.getServer().getTicks();
        if (now == session.lastSave) return;
        session.lastSave = now;
        ServerPlayerEntity target = session.target;
        if (!allowed(editor, target) || !online(target) || now - session.opened > MAX_SESSION_TICKS) {
            end(editor, session, "This party edit session ended. Open it again to continue.");
            return;
        }

        try {
            PartyStore store = PokemonLookup.party(target);
            if (inBattle(editor) || inBattle(target)) {
                send(editor, session, store, "Finish the current battle before editing a party.", false, false);
                return;
            }
            int slot = request.slot();
            if (slot < 0 || slot >= SLOT_COUNT || request.revision() != session.revision) {
                send(editor, session, store, "The party changed. Review the refreshed values.", false, false);
                return;
            }
            Pokemon live = store.get(slot);
            UUID currentId = live == null ? null : live.getUuid();
            if (!Objects.equals(request.pokemonId(), currentId)
                    || !Objects.equals(session.slots[slot], SlotState.of(live))) {
                send(editor, session, store, "That Pokémon changed or moved. Review the refreshed values.",
                        false, false);
                return;
            }

            PartyEdit edit;
            try {
                edit = PartyEdit.prepare(live, target.getUuid(), request);
            } catch (PartyEdit.Invalid invalid) {
                send(editor, session, store, invalid.getMessage(), false, false);
                return;
            }
            edit.apply();
            Pokemon edited = edit.pokemon();
            if (live == null) {
                store.set(slot, edited);
            } else {
                // Refresh this slot for all Cobblemon observers, including direct stat bonus NBT.
                store.sendPacketToObservers(new SetPartyPokemonPacket(store.getUuid(), new PartyPosition(slot),
                        registry -> edited));
            }
            StatEditorMod.LOGGER.info("[PokéEdit] {} saved slot {} ({}, {}) of {}", editor.getGameProfile().getName(),
                    slot + 1, edited.getSpecies().getResourceIdentifier(), edited.getUuid(),
                    target.getGameProfile().getName());
            send(editor, session, store, "Saved.", false, true);
        } catch (RuntimeException exception) {
            StatEditorMod.LOGGER.error("Could not finish the party edit of {}", target.getUuid(), exception);
            end(editor, session, "The server could not finish this edit. Reopen the party to check the current values.");
        }
    }

    private static void send(ServerPlayerEntity editor, Session session, PartyStore store, String notice,
                             boolean open, boolean saved) {
        Pokemon[] party = new Pokemon[SLOT_COUNT];
        int[][] stats = new int[SLOT_COUNT][StatIds.EDITABLE.size()];
        Integer[][] bonuses = new Integer[SLOT_COUNT][StatIds.EDITABLE.size()];
        for (int i = 0; i < party.length; i++) {
            Pokemon pokemon = store.get(i);
            party[i] = pokemon;
            session.slots[i] = SlotState.of(pokemon);
            if (pokemon == null) continue;
            for (int j = 0; j < StatIds.EDITABLE.size(); j++) {
                var stat = StatIds.EDITABLE.get(j);
                stats[i][j] = pokemon.getStat(stat);
                bonuses[i][j] = StatOverrideStore.getBonus(pokemon, stat);
            }
        }
        ServerPlayNetworking.send(editor, new PartySnapshot(session.token, ++session.revision, session.targetName,
                notice, open, StatEditorConfig.minValue, StatEditorConfig.maxValue, party, stats, bonuses, saved, false));
    }

    private static void end(ServerPlayerEntity editor, Session session, String notice) {
        SESSIONS.remove(editor.getUuid());
        ServerPlayNetworking.send(editor, new PartySnapshot(session.token, ++session.revision, session.targetName,
                notice, false, StatEditorConfig.minValue, StatEditorConfig.maxValue,
                new Pokemon[SLOT_COUNT], new int[SLOT_COUNT][], new Integer[SLOT_COUNT][], false, true));
    }

    private static boolean allowed(ServerPlayerEntity editor, ServerPlayerEntity target) {
        return editor == target ? StatEditorPermissions.canEditOwn(editor) : StatEditorPermissions.canEditOthers(editor);
    }

    private static boolean online(ServerPlayerEntity player) {
        return player.isAlive() && player.getServer().getPlayerManager().getPlayer(player.getUuid()) == player;
    }

    private static boolean inBattle(ServerPlayerEntity player) {
        return BattleRegistry.getBattleByParticipatingPlayerId(player.getUuid()) != null;
    }

    private static void message(ServerPlayerEntity player, String key) {
        player.sendMessage(Text.translatable(key), false);
    }

    public static void disconnect(ServerPlayerEntity player) {
        SESSIONS.remove(player.getUuid());
        LAST_OPEN.remove(player.getUuid());
    }

    public static void clear() {
        SESSIONS.clear();
        LAST_OPEN.clear();
    }

    private static final class Session {
        private final UUID token = UUID.randomUUID();
        private final ServerPlayerEntity target;
        private final int opened;
        private final String targetName;
        private final SlotState[] slots = new SlotState[SLOT_COUNT];
        private int revision;
        private int lastSave = -1;

        private Session(ServerPlayerEntity target, int opened, String targetName) {
            this.target = target;
            this.opened = opened;
            this.targetName = targetName;
        }
    }

    private record SlotState(UUID id, Identifier species, String form, int level, Text nickname,
                             Identifier nature, Identifier mint, List<Integer> ivs, List<Integer> evs,
                             List<String> moves, List<Integer> bonuses) {
        private static SlotState of(Pokemon pokemon) {
            if (pokemon == null) return null;
            return new SlotState(pokemon.getUuid(), pokemon.getSpecies().getResourceIdentifier(),
                    pokemon.getForm().getName(), pokemon.getLevel(),
                    pokemon.getNickname() == null ? null : pokemon.getNickname().copy(),
                    pokemon.getNature().getName(),
                    pokemon.getMintedNature() == null ? null : pokemon.getMintedNature().getName(),
                    StatIds.EDITABLE.stream().map(pokemon.getIvs()::getOrDefault).toList(),
                    StatIds.EDITABLE.stream().map(pokemon.getEvs()::getOrDefault).toList(),
                    PartyEdit.moveNames(pokemon),
                    StatIds.EDITABLE.stream().map(stat -> StatOverrideStore.getBonus(pokemon, stat)).toList());
        }
    }
}
