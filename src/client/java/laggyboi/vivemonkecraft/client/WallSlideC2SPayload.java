package laggyboi.vivemonkecraft.client;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client → server: "I am gripping right now (climbing or sliding)" keepalive.
 *
 * Fall damage is computed SERVER-SIDE from the server's own fallDistance, so the
 * client can't cancel it on a dedicated server by resetting its local player. We
 * tell the monke-server companion when we're gripping; it zeroes our fallDistance
 * each tick we are, giving the same graded result as singleplayer: slide all the
 * way down = no damage, let go partway = damage only from the release point.
 *
 * The client sends {@code gripping=true} every gripping tick (keepalive) and one
 * {@code gripping=false} on release. The server also auto-expires the flag after a
 * short grace window, so a dropped release packet can't leave a player permanently
 * immune to fall damage.
 *
 * Wire format: one boolean. MUST match the server mod's mirror class.
 */
public record WallSlideC2SPayload(boolean gripping) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<WallSlideC2SPayload> ID =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath("vivemonkecraft", "wall_slide"));

    public static final StreamCodec<FriendlyByteBuf, WallSlideC2SPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> buf.writeBoolean(p.gripping()),
                    buf -> new WallSlideC2SPayload(buf.readBoolean()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
