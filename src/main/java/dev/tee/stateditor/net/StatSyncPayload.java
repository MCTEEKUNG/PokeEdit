package dev.tee.stateditor.net;

import dev.tee.stateditor.StatEditorMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Server to client notification carrying the authoritative signed bonus, not the final stat.
 */
public record StatSyncPayload(UUID pokemonId, int entityId, String statKey, int bonus, boolean clear)
        implements CustomPayload {
    public static final CustomPayload.Id<StatSyncPayload> ID =
            new CustomPayload.Id<>(Identifier.of(StatEditorMod.MOD_ID, "stat_sync_bonus"));

    public static final PacketCodec<RegistryByteBuf, StatSyncPayload> CODEC = CustomPayload.codecOf(
            (StatSyncPayload payload, RegistryByteBuf buf) -> payload.write(buf),
            StatSyncPayload::new);

    public void write(RegistryByteBuf buf) {
        buf.writeUuid(this.pokemonId);
        buf.writeVarInt(this.entityId);
        buf.writeString(this.statKey);
        buf.writeVarInt(this.bonus);
        buf.writeBoolean(this.clear);
    }

    private StatSyncPayload(RegistryByteBuf buf) {
        this(buf.readUuid(), buf.readVarInt(), buf.readString(), buf.readVarInt(), buf.readBoolean());
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
