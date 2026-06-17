package laggyboi.vivemonkecraft.client;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client → server: "my monke model (legless look) is on/off".
 * The server tracks it and broadcasts {@code monke_model_sync} to everyone so
 * every player with the mod renders me accordingly.
 *
 * Wire format: one boolean. MUST match the server mod's mirror class.
 */
public record MonkeModelC2SPayload(boolean enabled) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MonkeModelC2SPayload> ID =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath("vivemonkecraft", "monke_model"));

    public static final StreamCodec<FriendlyByteBuf, MonkeModelC2SPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> buf.writeBoolean(p.enabled()),
                    buf -> new MonkeModelC2SPayload(buf.readBoolean()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
