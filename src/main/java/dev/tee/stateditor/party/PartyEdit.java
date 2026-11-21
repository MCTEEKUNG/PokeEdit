package dev.tee.stateditor.party;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveSet;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.pokemon.Natures;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.net.serverhandling.pokemon.update.SetNicknameHandler;
import com.cobblemon.mod.common.pokemon.EVs;
import com.cobblemon.mod.common.pokemon.IVs;
import com.cobblemon.mod.common.pokemon.Nature;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import dev.tee.stateditor.config.StatEditorConfig;
import dev.tee.stateditor.data.StatIds;
import dev.tee.stateditor.data.StatOverrideStore;
import dev.tee.stateditor.net.SaveParty;
import kotlin.Unit;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.StringHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** Validates and prepares a single slot edit before it touches the authoritative Pokémon. */
record PartyEdit(Pokemon pokemon, Species species, String nickname, Nature nature,
                 int[] ivs, int[] evs, Move[] moves, Integer[] stats) {
    static PartyEdit prepare(Pokemon live, UUID ownerId, SaveParty request) {
        Identifier speciesId = Identifier.tryParse(request.species());
        Species species = speciesId == null ? null : PokemonSpecies.getByIdentifier(speciesId);
        if (species == null) throw new Invalid("Choose a registered Pokémon species.");

        String nickname = request.nickname();
        if (nickname != null && (nickname.length() > SetNicknameHandler.MAX_NAME_LENGTH
                || !StringHelper.stripInvalidChars(nickname).equals(nickname))) {
            throw new Invalid("Use a valid nickname of up to " + SetNicknameHandler.MAX_NAME_LENGTH + " characters.");
        }

        Nature nature = null;
        if (!request.nature().isEmpty()) {
            Identifier natureId = Identifier.tryParse(request.nature());
            nature = natureId == null ? null : Natures.getNature(natureId);
            if (nature == null) throw new Invalid("Choose a registered nature.");
        }

        validateValues(request.ivs(), IVs.MAX_VALUE, "IV values must be 0–31.");
        validateValues(request.evs(), EVs.MAX_STAT_VALUE, "EV values must be 0–252.");
        Integer[] stats = request.stats();
        if (stats != null && (stats.length != StatIds.EDITABLE.size()
                || Arrays.stream(stats).anyMatch(value -> value != null && value != 0
                && !StatEditorConfig.isValidValue(value)))) {
            throw new Invalid("Direct stats must be in the server's allowed range.");
        }

        Pokemon pokemon = live != null ? live : species.create(1);
        if (live == null) pokemon.setOriginalTrainer(ownerId);
        if (request.evs() != null && finalEvTotal(pokemon, request.evs()) > EVs.MAX_TOTAL_VALUE) {
            throw new Invalid("Total EVs cannot exceed 510.");
        }

        return new PartyEdit(pokemon, species, nickname, nature, request.ivs(), request.evs(),
                prepareMoves(pokemon, request.moves()), stats);
    }

    private static void validateValues(int[] values, int maximum, String message) {
        if (values == null) return;
        if (values.length != StatIds.EDITABLE.size()) throw new Invalid(message);
        for (int value : values) {
            if (value < -1 || value > maximum) throw new Invalid(message);
        }
    }

    private static int finalEvTotal(Pokemon pokemon, int[] values) {
        int total = pokemon.getEvs().total();
        for (int i = 0; i < values.length; i++) {
            if (values[i] >= 0) total += values[i] - pokemon.getEvs().getOrDefault(StatIds.EDITABLE.get(i));
        }
        return total;
    }

    private static Move[] prepareMoves(Pokemon pokemon, List<String> names) {
        if (names == null || names.equals(moveNames(pokemon))) return null;
        if (names.size() != MoveSet.MOVE_COUNT) throw new Invalid("Choose up to four moves.");
        var existing = new HashMap<String, Move>();
        for (Move move : pokemon.getMoveSet()) existing.put(move.getTemplate().getName(), move);
        var seen = new HashSet<String>();
        Move[] result = new Move[MoveSet.MOVE_COUNT];
        for (int i = 0; i < result.length; i++) {
            String name = names.get(i);
            if (name.isEmpty()) continue;
            var template = Moves.getByName(name);
            if (template == null || !seen.add(template.getName())) {
                throw new Invalid("Choose registered moves without duplicates.");
            }
            Move current = existing.get(template.getName());
            if (current == null) result[i] = template.create();
            else result[i] = current == pokemon.getMoveSet().get(i) ? current : current.copy();
        }
        if (seen.isEmpty()) throw new Invalid("Keep at least one move.");
        return result;
    }

    void apply() {
        if (!pokemon.getSpecies().getResourceIdentifier().equals(species.getResourceIdentifier())) {
            int level = pokemon.getLevel();
            var oldGrowth = pokemon.getExperienceGroup();
            pokemon.setSpecies(species);
            if (pokemon.getExperienceGroup() != oldGrowth) pokemon.setLevel(level);
        }
        if (nickname != null && !nickname.equals(nickname(pokemon))) {
            pokemon.setNickname(nickname.isEmpty() ? null : Text.literal(nickname));
        }
        if (nature != null && (nature != pokemon.getNature() || pokemon.getMintedNature() != null)) {
            pokemon.setNature(nature);
            if (pokemon.getMintedNature() != null) pokemon.setMintedNature(null);
        }
        applyTraining();
        if (moves != null) {
            MoveSet moveSet = pokemon.getMoveSet();
            moveSet.doWithoutEmitting(() -> {
                for (int i = 0; i < moves.length; i++) {
                    if (moves[i] != moveSet.get(i)) moveSet.setMove(i, moves[i]);
                }
                return Unit.INSTANCE;
            });
            moveSet.update();
        }
        if (stats != null) {
            for (int i = 0; i < stats.length; i++) {
                Integer value = stats[i];
                if (value == null) continue;
                Stat stat = StatIds.EDITABLE.get(i);
                if (value == 0) StatOverrideStore.clear(pokemon, stat);
                else StatOverrideStore.set(pokemon, stat, value);
            }
        }
        pokemon.setCurrentHealth(Math.min(pokemon.getCurrentHealth(), pokemon.getMaxHealth()));
        pokemon.onChange(null);
    }

    private void applyTraining() {
        if (ivs != null) {
            for (int i = 0; i < ivs.length; i++) {
                Stat stat = StatIds.EDITABLE.get(i);
                if (ivs[i] >= 0 && ivs[i] != pokemon.getIvs().getOrDefault(stat)) pokemon.setIV(stat, ivs[i]);
            }
        }
        if (evs == null) return;
        // Free the EV budget before raising any other stat. Cobblemon checks every individual change.
        for (int i = 0; i < evs.length; i++) {
            Stat stat = StatIds.EDITABLE.get(i);
            if (evs[i] >= 0 && evs[i] < pokemon.getEvs().getOrDefault(stat)) pokemon.setEV(stat, evs[i]);
        }
        for (int i = 0; i < evs.length; i++) {
            Stat stat = StatIds.EDITABLE.get(i);
            if (evs[i] > pokemon.getEvs().getOrDefault(stat)) pokemon.setEV(stat, evs[i]);
        }
    }

    static String nickname(Pokemon pokemon) {
        return pokemon.getNickname() == null ? "" : pokemon.getNickname().getString();
    }

    static List<String> moveNames(Pokemon pokemon) {
        List<String> result = new ArrayList<>(MoveSet.MOVE_COUNT);
        for (int i = 0; i < MoveSet.MOVE_COUNT; i++) {
            Move move = pokemon.getMoveSet().get(i);
            result.add(move == null ? "" : move.getTemplate().getName());
        }
        return List.copyOf(result);
    }

    static final class Invalid extends RuntimeException {
        Invalid(String message) {
            super(message);
        }
    }
}
