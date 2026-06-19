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
// The setTeleportOverride / isTeleportEnabled route turned out unreliable, so we block
// at the source instead: TeleportTracker processes teleport input each tick and calls
// LocalPlayer.moveTo(x,y,z). Cancel at HEAD while gorilla locomotion is on and the
// player hasn't opted into allowTeleport so the button does nothing.
//
// Vivecraft 1.2.x: method is doProcess(LocalPlayer) — descriptor uses the intermediary
//   name class_746 because 1.2.x was compiled against intermediary mappings.
// Vivecraft 1.3.x: doProcess was split into activeProcess(LocalPlayer) /
//   inactiveProcess(LocalPlayer). We target activeProcess (the one that fires aim + move).
//
// Both injections have require = 0 so whichever version is absent simply no-ops.
// =====================================================================
@Mixin(targets = "org.vivecraft.client_vr.gameplay.trackers.TeleportTracker", remap = false)
public class TeleportTrackerMixin {

    // Vivecraft 1.2.x (QuestCraft / QCXR) — intermediary descriptor
    @Inject(method = "doProcess(Lnet/minecraft/class_746;)V",
            at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void vmcTp$blockTeleportLegacy(LocalPlayer player, CallbackInfo ci) {
        if (VivemonkecraftClient.isEnabled() && !MovementConfig.allowTeleport) {
            ci.cancel();
        }
    }

    // Vivecraft 1.3.x (PCVR / 1.21.8+) — Mojmap descriptor
    @Inject(method = "activeProcess(Lnet/minecraft/client/player/LocalPlayer;)V",
            at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void vmcTp$blockTeleportPublic(LocalPlayer player, CallbackInfo ci) {
        if (VivemonkecraftClient.isEnabled() && !MovementConfig.allowTeleport) {
            ci.cancel();
        }
    }
}
