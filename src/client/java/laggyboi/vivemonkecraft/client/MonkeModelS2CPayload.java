package laggyboi.vivemonkecraft.client;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → client: "player <uuid> has the monke model (legless look) on/off".
 * Sent to everyone when a player toggles, and replayed to joiners so they get
 * the full current set.
 *
 * Wire format: UUID + boolean. MUST match the server mod's mirror class.
 */
public record MonkeModelS2CPayload(UUID player, boolean enabled) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MonkeModelS2CPayload> ID =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("vivemonkecraft", "monke_model_sync"));

    public static final StreamCodec<FriendlyByteBuf, MonkeModelS2CPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> { buf.writeUUID(p.player()); buf.writeBoolean(p.enabled()); },
                    buf -> new MonkeModelS2CPayload(buf.readUUID(), buf.readBoolean()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
