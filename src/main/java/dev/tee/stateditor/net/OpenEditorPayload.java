package dev.tee.stateditor.net;

import dev.tee.stateditor.StatEditorMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server to client request: open the stat editor.
 *
 * @param entityId the Pokémon entity in the world to focus on, or {@code -1} for the own party
 */
public record OpenEditorPayload(int entityId) implements CustomPayload {
    public static final CustomPayload.Id<OpenEditorPayload> ID =
            new CustomPayload.Id<>(Identifier.of(StatEditorMod.MOD_ID, "open_editor"));

    public static final PacketCodec<RegistryByteBuf, OpenEditorPayload> CODEC = CustomPayload.codecOf(
            (OpenEditorPayload payload, RegistryByteBuf buf) -> payload.write(buf),
            OpenEditorPayload::new);

    public void write(RegistryByteBuf buf) {
        buf.writeVarInt(this.entityId);
    }

    private OpenEditorPayload(RegistryByteBuf buf) {
        this(buf.readVarInt());
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
