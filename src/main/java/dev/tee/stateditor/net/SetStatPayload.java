package dev.tee.stateditor.net;

import dev.tee.stateditor.StatEditorMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Client to server request: set (or clear) one stat of one Pokémon.
 *
 * @param pokemonId the Pokémon to change, ignored when {@code entityId} points at a Pokémon entity
 * @param entityId  the id of a Pokémon entity in the world, or {@code -1} to use the party/PC
 * @param statKey   the stat to change, e.g. {@code cobblemon:attack}
 * @param value     the new value
 * @param clear     whether the override should be removed instead
 */
public record SetStatPayload(UUID pokemonId, int entityId, String statKey, int value, boolean clear)
        implements CustomPayload {
    public static final CustomPayload.Id<SetStatPayload> ID =
            new CustomPayload.Id<>(Identifier.of(StatEditorMod.MOD_ID, "set_stat"));

    public static final PacketCodec<RegistryByteBuf, SetStatPayload> CODEC = CustomPayload.codecOf(
            (SetStatPayload payload, RegistryByteBuf buf) -> payload.write(buf),
            SetStatPayload::new);

    public void write(RegistryByteBuf buf) {
        buf.writeUuid(this.pokemonId);
        buf.writeVarInt(this.entityId);
        buf.writeString(this.statKey);
        buf.writeVarInt(this.value);
        buf.writeBoolean(this.clear);
    }

    private SetStatPayload(RegistryByteBuf buf) {
        this(buf.readUuid(), buf.readVarInt(), buf.readString(), buf.readVarInt(), buf.readBoolean());
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
