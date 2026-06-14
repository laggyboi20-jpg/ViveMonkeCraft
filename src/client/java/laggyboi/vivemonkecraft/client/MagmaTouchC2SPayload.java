package laggyboi.vivemonkecraft.client;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client → server: "a hand is gripping a magma block this tick — hurt me."
 *
 * Magma damage is server-authoritative, so a client-only install can't apply it on a
 * dedicated server. The client sends this each tick a hand touches a magma block; the
 * companion mod applies hot-floor damage to the sender (vanilla invulnerability frames
 * throttle the cadence, and fire resistance negates it — same as standing on magma).
 *
 * No payload data — its presence is the whole message. MUST match the server mirror.
 */
public record MagmaTouchC2SPayload() implements CustomPacketPayload {

    public static final MagmaTouchC2SPayload INSTANCE = new MagmaTouchC2SPayload();

    public static final CustomPacketPayload.Type<MagmaTouchC2SPayload> ID =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("vivemonkecraft", "magma_touch"));

    public static final StreamCodec<FriendlyByteBuf, MagmaTouchC2SPayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
