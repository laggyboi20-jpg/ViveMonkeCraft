package laggyboi.vivemonkecraft.client;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client-side mirror of the server mod's ServerConfigPayload.
 *
 * Wire format MUST match the server copy exactly:
 *   boolean modEnabled, then 10 doubles in the order below.
 *
 * Enforced limits: modEnabled, maxJumpSpeed (backed up server-side).
 * Allowances: per-setting limits the admin GRANTS to all players, replacing the
 * client default caps in SettingCaps. 0 = not granted (max-type), -1 = not
 * granted (min-type: gravity / air friction, where 0 is a meaningful value).
 */
public record ServerConfigPayload(
        boolean modEnabled,
        double  maxJumpSpeed,
        double  allowPushStrength,
        double  allowJumpMultiplier,
        double  allowMaxJumpSpeed,
        double  allowHandReach,
        double  allowArmLength,
        double  allowStepHeight,
        double  allowHandRadius,
        double  allowGravityMin,
        double  allowAirFrictionMin
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ServerConfigPayload> ID =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath("vivemonkecraft", "server_cfg"));

    public static final StreamCodec<FriendlyByteBuf, ServerConfigPayload> STREAM_CODEC =
            StreamCodec.of(ServerConfigPayload::encode, ServerConfigPayload::decode);

    private static void encode(FriendlyByteBuf buf, ServerConfigPayload p) {
        buf.writeBoolean(p.modEnabled());
        buf.writeDouble(p.maxJumpSpeed());
        buf.writeDouble(p.allowPushStrength());
        buf.writeDouble(p.allowJumpMultiplier());
        buf.writeDouble(p.allowMaxJumpSpeed());
        buf.writeDouble(p.allowHandReach());
        buf.writeDouble(p.allowArmLength());
        buf.writeDouble(p.allowStepHeight());
        buf.writeDouble(p.allowHandRadius());
        buf.writeDouble(p.allowGravityMin());
        buf.writeDouble(p.allowAirFrictionMin());
    }

    private static ServerConfigPayload decode(FriendlyByteBuf buf) {
        return new ServerConfigPayload(
                buf.readBoolean(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble()
        );
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
