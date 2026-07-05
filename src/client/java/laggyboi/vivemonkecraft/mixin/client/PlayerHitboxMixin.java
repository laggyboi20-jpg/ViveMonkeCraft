package laggyboi.vivemonkecraft.mixin.client;

import laggyboi.vivemonkecraft.client.EmbeddedServerLogic;
import laggyboi.vivemonkecraft.client.MovementConfig;
import laggyboi.vivemonkecraft.client.VivemonkecraftClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
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

// TARGET: LivingEntity#getDefaultDimensions — NOT Entity#getDimensions! Since 1.20.5,
// LivingEntity overrides getDimensions() as getDefaultDimensions(pose).scale(getScale())
// without calling super, so an Entity.getDimensions injection NEVER RUNS for
// players (it was silently dead — and exactly why only the SCALE attribute,
// which feeds getScale(), ever managed to shrink the box).
//
// WHY LivingEntity AND NOT Player: through 1.21.8, Player OVERRODE
// getDefaultDimensions, so we targeted Player.class. In 1.21.9 Player DROPPED that
// override and now inherits LivingEntity's — so a Player.class injection finds no
// method (require=0 → silently no-ops), which is why the Real Monke 0.5 shrink went
// dead on 1.21.9+. Targeting LivingEntity catches the inherited method that players
// actually run. The body gates on instanceof LocalPlayer/ServerPlayer and returns
// early for every other LivingEntity, so non-players are untouched.
// priority 2000 (default 1000): Vivecraft also manages player sizing/poses —
// applying later means OUR setReturnValue runs last and wins.
@Mixin(value = LivingEntity.class, priority = 2000)
public class PlayerHitboxMixin {

    // Throttles for the diagnostic HITBOX logs (see below) — once per second each.
    @org.spongepowered.asm.mixin.Unique
    private static long vmc$lastLogMs = 0L;
    @org.spongepowered.asm.mixin.Unique
    private static long vmc$lastEntryMs = 0L;

    // DIAGNOSTIC: target getDimensions (public final on LivingEntity, definitely called
    // for players) instead of the protected getDefaultDimensions, which was silently
    // failing to apply on 26.2. require = 1 TEMPORARILY so any apply failure crashes
    // LOUDLY with the exact reason instead of being hidden — revert to require = 0 once
    // this is confirmed working.
    @Inject(
        method = "getDimensions(Lnet/minecraft/world/entity/Pose;)Lnet/minecraft/world/entity/EntityDimensions;",
        at = @At("RETURN"),
        cancellable = true,
        require = 1
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
            // DIAGNOSTIC (debug log only): fires EVERY time getDefaultDimensions runs for
            // our own player, BEFORE any enabled/config gate. If you enable debug logging
            // and see NO "getDefaultDimensions RAN" lines at all, the mixin is NOT being
            // applied to LivingEntity at runtime (the real problem). If you DO see them
            // but never a "shrink FIRED" line, the mixin runs but the enabled/config gate
            // is stopping the shrink. Throttled to once per second.
            long entryNow = System.currentTimeMillis();
            if (MovementConfig.debugLogging && entryNow - vmc$lastEntryMs > 1000L) {
                vmc$lastEntryMs = entryNow;
                laggyboi.vivemonkecraft.client.VmcDebugLog.event("HITBOX",
                    "getDefaultDimensions RAN for own player (mixin IS applied) side="
                    + (self instanceof ServerPlayer ? "server" : "client")
                    + " enabled=" + VivemonkecraftClient.isEnabled()
                    + " realMonke=" + MovementConfig.realMonke
                    + " scale=" + MovementConfig.hitboxHeightScale + " pose=" + pose);
            }

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
                EntityDimensions out = vmc$collisionOnly(dims, original);
                cir.setReturnValue(out);
                // DIAGNOSTIC (debug log only): proves the shrink mixin is actually firing
                // and what height it produces. Throttled to once per second so it doesn't
                // flood the log. If you enable debug logging, toggle Real Monke, and see
                // NO "HITBOX shrink" lines, the mixin isn't applying (stale jar / not
                // installed). If you see them but tunnels still fail, the box is fine and
                // the problem is elsewhere (server-side validation, camera, etc.).
                long now = System.currentTimeMillis();
                if (MovementConfig.debugLogging && now - vmc$lastLogMs > 1000L) {
                    vmc$lastLogMs = now;
                    laggyboi.vivemonkecraft.client.VmcDebugLog.event("HITBOX",
                        "shrink FIRED side=" + (self instanceof ServerPlayer ? "server" : "client")
                        + " pose=" + pose + " realMonke=" + MovementConfig.realMonke
                        + " origH=" + original.height() + " -> newH=" + out.height());
                }
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
