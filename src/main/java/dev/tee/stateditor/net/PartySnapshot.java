package dev.tee.stateditor.net;

import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.tee.stateditor.StatEditorMod;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

/** Sends the six public Cobblemon Pokémon views and their effective stats to the active editor. */
public record PartySnapshot(UUID token, int revision, String targetName, String notice, boolean open,
                            int minimum, int maximum, Pokemon[] party, int[][] stats, Integer[][] bonuses,
                            boolean saved, boolean terminal) implements CustomPayload {
    public static final Id<PartySnapshot> ID = new Id<>(Identifier.of(StatEditorMod.MOD_ID, "party_snapshot"));
    public static final PacketCodec<RegistryByteBuf, PartySnapshot> CODEC =
            CustomPayload.codecOf(PartySnapshot::write, PartySnapshot::read);

    private void write(RegistryByteBuf buf) {
        buf.writeUuid(token);
        buf.writeVarInt(revision);
        buf.writeString(targetName, 256);
        buf.writeString(notice, 512);
        buf.writeBoolean(open);
        buf.writeBoolean(saved);
        buf.writeBoolean(terminal);
        buf.writeVarInt(minimum);
        buf.writeVarInt(maximum);
        for (int i = 0; i < 6; i++) {
            buf.writeBoolean(party[i] != null);
            if (party[i] == null) continue;
            Pokemon.getS2C_CODEC().encode(buf, party[i]);
            for (int j = 0; j < 6; j++) {
                buf.writeVarInt(stats[i][j]);
                buf.writeNullable(bonuses[i][j], PacketByteBuf::writeVarInt);
            }
        }
    }

    private static PartySnapshot read(RegistryByteBuf buf) {
        UUID token = buf.readUuid();
        int revision = buf.readVarInt();
        String name = buf.readString(256);
        String notice = buf.readString(512);
        boolean open = buf.readBoolean();
        boolean saved = buf.readBoolean();
        boolean terminal = buf.readBoolean();
        int minimum = buf.readVarInt();
        int maximum = buf.readVarInt();
        Pokemon[] party = new Pokemon[6];
        int[][] stats = new int[6][6];
        Integer[][] bonuses = new Integer[6][6];
        for (int i = 0; i < 6; i++) {
            if (!buf.readBoolean()) continue;
            party[i] = Pokemon.getS2C_CODEC().decode(buf);
            for (int j = 0; j < 6; j++) {
                stats[i][j] = buf.readVarInt();
                bonuses[i][j] = buf.readNullable(PacketByteBuf::readVarInt);
            }
        }
        return new PartySnapshot(token, revision, name, notice, open, minimum, maximum,
                party, stats, bonuses, saved, terminal);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
