package laggyboi.vivemonkecraft.client;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client → server request: apply Real Monke (height-only hitbox shrink) to me.
 *
 * The server validates movement against ITS copy of the player's box, so the
 * shrink must exist server-side too or 1-block tunnels get rubber-banded. On a
 * dedicated server the monke-server companion tracks who opted in and caps
 * their box height via its own mixin. The packet carries only on/off intent.
 *
 * Wire format: one boolean. MUST match the server mod's mirror class.
 */
public record RealMonkeC2SPayload(boolean enabled) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RealMonkeC2SPayload> ID =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("vivemonkecraft", "real_monke"));

    public static final StreamCodec<FriendlyByteBuf, RealMonkeC2SPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> buf.writeBoolean(p.enabled()),
                    buf -> new RealMonkeC2SPayload(buf.readBoolean()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
