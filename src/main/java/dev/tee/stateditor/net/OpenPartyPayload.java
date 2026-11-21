package dev.tee.stateditor.net;

import dev.tee.stateditor.StatEditorMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Opens the sender's own party. Targeted sessions are opened by server interactions. */
public record OpenPartyPayload() implements CustomPayload {
    public static final Id<OpenPartyPayload> ID = new Id<>(Identifier.of(StatEditorMod.MOD_ID, "open_party"));
    public static final PacketCodec<RegistryByteBuf, OpenPartyPayload> CODEC = PacketCodec.unit(new OpenPartyPayload());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
