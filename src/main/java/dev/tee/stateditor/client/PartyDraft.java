package dev.tee.stateditor.client;

import com.cobblemon.mod.common.api.moves.MoveSet;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.pokemon.EVs;
import com.cobblemon.mod.common.pokemon.IVs;
import com.cobblemon.mod.common.pokemon.Nature;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import dev.tee.stateditor.data.StatIds;
import dev.tee.stateditor.net.PartySnapshot;
import dev.tee.stateditor.net.SaveParty;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;

/** Unsent changes for a single slot. Blank numeric inputs leave the server's value in place. */
final class PartyDraft {
    static final int STAT_COUNT = StatIds.EDITABLE.size();
    static final int MOVE_COUNT = MoveSet.MOVE_COUNT;

    final Pokemon original;
    final String[] totals = new String[STAT_COUNT];
    final String[] ivs = new String[STAT_COUNT];
    final String[] evs = new String[STAT_COUNT];
    final String[] moves;
    private final String[] originalMoves = new String[MOVE_COUNT];
    private final String originalNickname;
    private final Nature originalNature;
    private final String originalSpeciesText;

    String speciesText;
    Species species;
    String nickname;
    Nature nature;

    PartyDraft(Pokemon original) {
        this.original = original;
        species = original == null ? null : original.getSpecies();
        originalSpeciesText = species == null ? "" : species.getTranslatedName().getString();
        speciesText = originalSpeciesText;
        originalNickname = original == null || original.getNickname() == null
                ? "" : original.getNickname().getString();
        nickname = originalNickname;
        originalNature = original == null ? null : original.getEffectiveNature();
        nature = originalNature;
        Arrays.fill(totals, "");
        Arrays.fill(ivs, "");
        Arrays.fill(evs, "");
        for (int i = 0; i < MOVE_COUNT; i++) {
            var move = original == null ? null : original.getMoveSet().get(i);
            originalMoves[i] = move == null ? "" : move.getName();
        }
        moves = originalMoves.clone();
    }

    boolean isDirty() {
        return !speciesText.equals(originalSpeciesText)
                || (original != null && species != original.getSpecies())
                || !nickname.equals(originalNickname)
                || nature != originalNature
                || Arrays.stream(totals).anyMatch(s -> !s.isEmpty())
                || Arrays.stream(ivs).anyMatch(s -> !s.isEmpty())
                || Arrays.stream(evs).anyMatch(s -> !s.isEmpty())
                || movesChanged();
    }

    boolean movesChanged() {
        return !Arrays.equals(moves, originalMoves);
    }

    boolean matchesOriginal(Pokemon pokemon) {
        if (original == null || pokemon == null) {
            return original == pokemon;
        }
        var fromServer = new PartyDraft(pokemon);
        if (!original.getUuid().equals(pokemon.getUuid())
                || original.getSpecies() != pokemon.getSpecies()
                || original.getForm() != pokemon.getForm()
                || original.getLevel() != pokemon.getLevel()
                || !Objects.equals(original.getNickname(), pokemon.getNickname())
                || originalNature != fromServer.originalNature
                || !Arrays.equals(originalMoves, fromServer.originalMoves)) {
            return false;
        }
        for (int i = 0; i < STAT_COUNT; i++) {
            if (currentIv(i) != fromServer.currentIv(i) || currentEv(i) != fromServer.currentEv(i)) {
                return false;
            }
        }
        return true;
    }

    int currentIv(int index) {
        return original == null ? -1 : original.getIvs().getOrDefault(StatIds.EDITABLE.get(index));
    }

    int currentEv(int index) {
        return original == null ? 0 : original.getEvs().getOrDefault(StatIds.EDITABLE.get(index));
    }

    static MoveTemplate findMove(String text) {
        String name = normalizeMove(text);
        if (name.isEmpty()) {
            return null;
        }
        var byId = Moves.getByName(name);
        if (byId != null) {
            return byId;
        }
        return Moves.all().stream()
                .filter(move -> normalizeMove(move.getDisplayName().getString()).equals(name))
                .findFirst().orElse(null);
    }

    static String normalizeMove(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    String validate(int minimum, int maximum) {
        if (species == null) {
            return "Choose a species to edit this slot.";
        }
        int evTotal = 0;
        for (int i = 0; i < STAT_COUNT; i++) {
            if (!validNumber(totals[i], minimum, maximum, true)) {
                return "Stat totals must be " + minimum + " to " + maximum + ". Use 0 to clear a bonus.";
            }
            if (!validNumber(ivs[i], 0, IVs.MAX_VALUE, false)) {
                return "IVs must be 0 to " + IVs.MAX_VALUE + ".";
            }
            if (!validNumber(evs[i], 0, EVs.MAX_STAT_VALUE, false)) {
                return "EVs must be 0 to " + EVs.MAX_STAT_VALUE + " per stat.";
            }
            evTotal += evs[i].isEmpty() ? currentEv(i) : Integer.parseInt(evs[i]);
        }
        if (evTotal > EVs.MAX_TOTAL_VALUE && Arrays.stream(evs).anyMatch(value -> !value.isEmpty())) {
            return "EVs may total at most " + EVs.MAX_TOTAL_VALUE + ".";
        }
        if (movesChanged()) {
            var chosen = new HashSet<String>();
            for (String move : moves) {
                if (move.isBlank()) {
                    continue;
                }
                var template = findMove(move);
                if (template == null) {
                    return "Unknown move: " + move;
                }
                if (!chosen.add(template.getName())) {
                    return "Each move can only be used once.";
                }
            }
            if (chosen.isEmpty()) {
                return "Choose at least one move.";
            }
        }
        return "";
    }

    SaveParty packet(PartySnapshot snapshot, int slot) {
        var statValues = new Integer[STAT_COUNT];
        for (int i = 0; i < STAT_COUNT; i++) {
            if (!totals[i].isEmpty()) {
                statValues[i] = Integer.parseInt(totals[i]);
            }
        }
        return new SaveParty(snapshot.token(), snapshot.revision(), slot,
                original == null ? null : original.getUuid(), species.getResourceIdentifier().toString(),
                nickname.equals(originalNickname) ? null : nickname,
                nature == null || nature == originalNature ? "" : nature.getName().toString(),
                numericChanges(ivs), numericChanges(evs),
                movesChanged() ? Arrays.stream(moves).map(move -> move.isBlank() ? "" : findMove(move).getName()).toList() : null, statValues);
    }

    private static int[] numericChanges(String[] values) {
        if (Arrays.stream(values).allMatch(String::isEmpty)) {
            return null;
        }
        return Arrays.stream(values).mapToInt(value -> value.isEmpty() ? -1 : Integer.parseInt(value)).toArray();
    }

    private static boolean validNumber(String raw, int minimum, int maximum, boolean allowClear) {
        if (raw.isEmpty()) {
            return true;
        }
        try {
            int value = Integer.parseInt(raw);
            return (allowClear && value == 0) || (value >= minimum && value <= maximum);
        } catch (NumberFormatException exception) {
            return false;
        }
    }
}
