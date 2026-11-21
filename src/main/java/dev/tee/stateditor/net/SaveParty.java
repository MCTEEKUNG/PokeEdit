package dev.tee.stateditor.net;

import dev.tee.stateditor.StatEditorMod;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Updates one occupied slot or fills one empty slot. A null Pokémon ID expects an empty slot.
 * Null edit sections leave existing or generated values intact, as do null direct stat entries
 * and -1 IV/EV entries. A direct stat value of zero removes its override.
 */
public record SaveParty(UUID token, int revision, int slot, UUID pokemonId, String species, String nickname,
                        String nature, int[] ivs, int[] evs, List<String> moves, Integer[] stats) implements CustomPayload {
    public static final Id<SaveParty> ID = new Id<>(Identifier.of(StatEditorMod.MOD_ID, "save_party"));
    public static final PacketCodec<RegistryByteBuf, SaveParty> CODEC =
            CustomPayload.codecOf(SaveParty::write, SaveParty::read);

    private void write(RegistryByteBuf buf) {
        buf.writeUuid(token);
        buf.writeVarInt(revision);
        buf.writeVarInt(slot);
        buf.writeNullable(pokemonId, (out, value) -> out.writeUuid(value));
        buf.writeString(species, 256);
        buf.writeNullable(nickname, (out, name) -> out.writeString(name, 64));
        buf.writeString(nature, 256);
        buf.writeNullable(ivs, SaveParty::writeStatArray);
        buf.writeNullable(evs, SaveParty::writeStatArray);
        buf.writeNullable(moves, (out, values) -> {
            for (String move : values) out.writeString(move, 64);
        });
        buf.writeNullable(stats, (out, values) -> {
            for (Integer value : values) out.writeNullable(value, PacketByteBuf::writeVarInt);
        });
    }

    private static SaveParty read(RegistryByteBuf buf) {
        UUID token = buf.readUuid();
        int revision = buf.readVarInt();
        int slot = buf.readVarInt();
        UUID pokemonId = buf.readNullable(in -> in.readUuid());
        String species = buf.readString(256);
        String nickname = buf.readNullable(in -> in.readString(64));
        String nature = buf.readString(256);
        int[] ivs = buf.readNullable(SaveParty::readStatArray);
        int[] evs = buf.readNullable(SaveParty::readStatArray);
        List<String> moves = buf.readNullable(in -> {
            List<String> values = new ArrayList<>(4);
            for (int i = 0; i < 4; i++) values.add(in.readString(64));
            return List.copyOf(values);
        });
        Integer[] stats = buf.readNullable(in -> {
            Integer[] values = new Integer[6];
            for (int i = 0; i < values.length; i++) {
                values[i] = in.readNullable(PacketByteBuf::readVarInt);
            }
            return values;
        });
        return new SaveParty(token, revision, slot, pokemonId, species, nickname, nature, ivs, evs, moves, stats);
    }

    private static void writeStatArray(PacketByteBuf buf, int[] values) {
        for (int value : values) buf.writeVarInt(value);
    }

    private static int[] readStatArray(PacketByteBuf buf) {
        int[] values = new int[6];
        for (int i = 0; i < values.length; i++) values[i] = buf.readVarInt();
        return values;
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
