package laggyboi.vivemonkecraft.mixin.client;

import laggyboi.vivemonkecraft.client.EmbeddedServerLogic;
import laggyboi.vivemonkecraft.client.MovementConfig;
import laggyboi.vivemonkecraft.client.VivemonkecraftClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// =====================================================================
// SHORTER HITBOX (mixin) — "delete the legs" for climbing
// =====================================================================
//
// A Minecraft player is ONE collision box (~0.6 wide x 1.8 tall) anchored at
// the feet — there are no separate "leg" boxes. So to stop your lower body
// catching on block edges when you climb, we make that box SHORTER.
//
// We hook getDimensions() (which decides the player's box size) and, only for
// YOUR player and only while the mod is on, scale the HEIGHT down.
// =====================================================================

// TARGET: Player#getDefaultDimensions — NOT Entity#getDimensions! Since 1.20.5,
// LivingEntity overrides getDimensions() as getDefaultDimensions(pose).scale(getScale())
// without calling super, so an Entity.getDimensions injection NEVER RUNS for
// players (it was silently dead — and exactly why only the SCALE attribute,
// which feeds getScale(), ever managed to shrink the box).
// priority 2000 (default 1000): Vivecraft also manages player sizing/poses —
// applying later means OUR setReturnValue runs last and wins.
@Mixin(value = Player.class, priority = 2000)
public class PlayerHitboxMixin {

    // require = 0 -> if Mojang renames this in a future version, we just skip the
    // shrink instead of crashing.
    @Inject(
        method = "getDefaultDimensions(Lnet/minecraft/world/entity/Pose;)Lnet/minecraft/world/entity/EntityDimensions;",
        at = @At("RETURN"),
        cancellable = true,
        require = 0
    )
    private void vmc$shrinkHitbox(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        // Apply to the local player on BOTH logical sides of this JVM:
        //   - LocalPlayer: the client-side entity (what your own collision uses)
        //   - the integrated server's ServerPlayer with OUR uuid (singleplayer /
        //     LAN host): the server validates movement against ITS box — if it
        //     stayed 1.8 tall, walking into a 1-block tunnel would be rubber-
        //     banded back even though the client box fits. Matching both sides
        //     is what makes tunnels actually work in singleplayer.
        //     (On dedicated servers the monke-server companion does this half.)
        Object self = this;

        // Is this OUR OWN player (the client entity, or the integrated server's copy
        // of us when hosting)? Our own box follows OUR live MovementConfig.
        boolean own;
        if (self instanceof LocalPlayer) {
            own = true;
        } else if (self instanceof ServerPlayer sp) {
            LocalPlayer lp = Minecraft.getInstance().player;
            own = lp != null && sp.getUUID().equals(lp.getUUID());
        } else {
            return;
        }

        if (own) {
            // Only while the mod is on.
            if (!VivemonkecraftClient.isEnabled()) return;

            EntityDimensions original = cir.getReturnValue();
            EntityDimensions dims     = original;

            // Scale HEIGHT only (1.0 width factor, `scale` height factor).
            double scale = MovementConfig.hitboxHeightScale;
            if (scale < 1.0 && scale > 0.0) {
                dims = dims.scale(1.0f, (float) scale);
            }

            // REAL MONKE: cap the box at 0.5 blocks — half of the ~2 m player, a true
            // one-block monke. 0.5 (not ~0.95) so there's real clearance in a 1-block
            // tunnel: at 0.95 any upward push (step assist, swing) pressed the box
            // into the ceiling and the server wedged/rubber-banded the movement.
            if (MovementConfig.realMonke && dims.height() > 0.5f) {
                dims = dims.scale(1.0f, 0.5f / dims.height());
            }

            if (dims != original) {
                cir.setReturnValue(vmc$collisionOnly(dims, original));
            }
            return;
        }

        // GUEST on our integrated server (LAN host): shrink any ServerPlayer who has
        // told us (via the embedded Real Monke receiver) that they're gorilla-sized,
        // so the host's movement validation agrees and their 1-block tunnels work.
        // Driven purely by the guest's request — NOT the host's own config.
        if (self instanceof ServerPlayer sp
                && EmbeddedServerLogic.realMonkePlayers.contains(sp.getUUID())) {
            EntityDimensions original = cir.getReturnValue();
            if (original.height() > 0.5f) {
                EntityDimensions dims = original.scale(1.0f, 0.5f / original.height());
                cir.setReturnValue(vmc$collisionOnly(dims, original));
            }
        }
    }

    // COLLISION-ONLY shrink: EntityDimensions.scale() also scales eyeHeight and the
    // render ATTACHMENTS, and Vivecraft anchors the player model (head, 3D layers,
    // VR body) off those — so scaling them dragged the model down and put your own
    // head into the camera. Rebuild with the shrunk box but the ORIGINAL eye height
    // and attachments, so ONLY collision changes and the model stays full-size and
    // correctly positioned (this is why the head was never in the way in dev-20).
    @org.spongepowered.asm.mixin.Unique
    private static EntityDimensions vmc$collisionOnly(EntityDimensions shrunk, EntityDimensions original) {
        return new EntityDimensions(
                shrunk.width(), shrunk.height(),
                original.eyeHeight(), original.attachments(), shrunk.fixed());
    }
}
