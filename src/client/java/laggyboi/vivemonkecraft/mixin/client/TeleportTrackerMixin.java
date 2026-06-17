package laggyboi.vivemonkecraft.mixin.client;

import laggyboi.vivemonkecraft.client.MovementConfig;
import laggyboi.vivemonkecraft.client.VivemonkecraftClient;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// =====================================================================
// BLOCK VIVECRAFT TELEPORT (mixin)
// =====================================================================
//
// Teleporting desyncs Vivecraft's room origin and breaks the gorilla hand physics.
// The setTeleportOverride / isTeleportEnabled route turned out unreliable (on this
// build isTeleportEnabled stays true regardless of the override), so we block at the
// source instead: TeleportTracker.doProcess is what AIMS and EXECUTES the teleport —
// at runtime it calls LocalPlayer.moveTo(x,y,z) + the teleport step callback. Cancel
// it at HEAD while gorilla locomotion is on and the player hasn't opted into
// allowTeleport, so the teleport button does nothing (no aim arc, no move, no desync).
// With the mod off, or allowTeleport on, doProcess runs normally and teleport works.
//
// Reflection-target mixin (no Vivecraft compile dependency); require = 0 so it simply
// no-ops if the class/method isn't present on some Vivecraft version.
// =====================================================================
@Mixin(targets = "org.vivecraft.client_vr.gameplay.trackers.TeleportTracker", remap = false)
public class TeleportTrackerMixin {

    @Inject(method = "doProcess(Lnet/minecraft/class_746;)V",
            at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void vmcTp$blockTeleport(LocalPlayer player, CallbackInfo ci) {
        if (VivemonkecraftClient.isEnabled() && !MovementConfig.allowTeleport) {
            ci.cancel();
        }
    }
}
