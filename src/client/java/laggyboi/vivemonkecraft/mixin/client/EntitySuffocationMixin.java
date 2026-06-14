package laggyboi.vivemonkecraft.mixin.client;

import laggyboi.vivemonkecraft.client.EmbeddedServerLogic;
import laggyboi.vivemonkecraft.client.MovementConfig;
import laggyboi.vivemonkecraft.client.VivemonkecraftClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// =====================================================================
// NO REAL-MONKE SUFFOCATION (mixin)
// =====================================================================
//
// Real Monke shrinks the COLLISION box to 0.5 (so you fit 1-block tunnels) but
// keeps the EYE height at full height (so the player model isn't dragged down).
// Side effect: in a 1-block tunnel your eye sits inside the ceiling block, and
// vanilla suffocation (Entity.isInWall → IN_WALL damage) starts hurting you.
//
// Fix: report isInWall() = false for a Real Monke player. The collision box
// genuinely fits, so the player isn't really stuck — only the (deliberately
// high) eye point overlaps the ceiling. Applies to your own player and, on an
// integrated/LAN server, to any guest who has Real Monke on.
//
// Reflection-free (vanilla types only) but kept on the client config; on a
// dedicated server the matching cancel lives in the monke-server mod.
// =====================================================================
@Mixin(Entity.class)
public class EntitySuffocationMixin {

    @Inject(method = "isInWall", at = @At("HEAD"), cancellable = true, require = 0)
    private void vmc$noMonkeSuffocation(CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof Player)) return;

        boolean cancel = false;
        if ((Object) this instanceof LocalPlayer) {
            cancel = VivemonkecraftClient.isEnabled() && MovementConfig.realMonke;
        } else if ((Object) this instanceof ServerPlayer sp) {
            LocalPlayer lp = Minecraft.getInstance().player;
            if (lp != null && sp.getUUID().equals(lp.getUUID())) {
                cancel = VivemonkecraftClient.isEnabled() && MovementConfig.realMonke;
            } else {
                cancel = EmbeddedServerLogic.realMonkePlayers.contains(sp.getUUID());
            }
        }

        if (cancel) cir.setReturnValue(false);
    }
}
