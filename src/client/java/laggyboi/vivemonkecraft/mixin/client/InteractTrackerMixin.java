package laggyboi.vivemonkecraft.mixin.client;

import laggyboi.vivemonkecraft.client.MovementConfig;
import laggyboi.vivemonkecraft.client.VivemonkecraftClient;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

// =====================================================================
// BLOCK-BREAK ALIGNMENT (mixin) — break the block you LOOK at, not the one above
// =====================================================================
//
// When the VR view is dropped (Real Monke / camera offset) we lower
// vrdata_world_RENDER so the VIEW sits lower. But roomscale block interaction
// (punching a block with your real hand) is computed by InteractTracker.doProcess
// from vrdata_world_PRE — which we DON'T lower — so the break ray fires from your
// true (higher) hand position and you break the block ~1 above what you see.
//
// FIX: lower vrdata_world_pre.origin by the SAME drop ONLY for the duration of
// doProcess (offset at HEAD, restore at RETURN). Every position doProcess reads
// (controllers, eye) derives from that origin, so the whole interaction ray drops
// to match the view — while movement, which reads vrdata_world_pre OUTSIDE
// doProcess, still sees the unmodified origin (that's why lowering it globally
// last time shoved the player into the floor; this is scoped to interaction only).
//
// Reflection-only (no Vivecraft compile dependency); require = 0 + a fail flag
// mean it silently no-ops if the internals differ on some Vivecraft version.
// =====================================================================
@Mixin(targets = "org.vivecraft.client_vr.gameplay.trackers.InteractTracker", remap = false)
public class InteractTrackerMixin {

    @Unique private static Method vmcIt$vrPlayerGet;   // VRPlayer.get()
    @Unique private static Field  vmcIt$preField;      // VRPlayer.vrdata_world_pre
    @Unique private static Field  vmcIt$originField;   // VRData.origin
    @Unique private static boolean vmcIt$broken = false;

    // Origin saved between HEAD and RETURN so we restore the exact value.
    @Unique private static Object vmcIt$preData = null;
    @Unique private static Vec3   vmcIt$saved   = null;

    @Inject(method = "doProcess(Lnet/minecraft/class_746;)V", at = @At("HEAD"), require = 0, remap = false)
    private void vmcIt$lowerForInteract(LocalPlayer player, CallbackInfo ci) {
        vmcIt$preData = null;
        vmcIt$saved   = null;
        if (vmcIt$broken) return;

        double drop = VivemonkecraftClient.isEnabled()
                ? (MovementConfig.realMonke ? 0.9 : MovementConfig.cameraHeightOffset)
                : 0.0;
        if (drop <= 0.0) return;

        try {
            if (vmcIt$vrPlayerGet == null) {
                Class<?> vrPlayer = Class.forName("org.vivecraft.client_vr.gameplay.VRPlayer");
                vmcIt$vrPlayerGet = vrPlayer.getMethod("get");
                vmcIt$preField    = vrPlayer.getField("vrdata_world_pre");
            }
            Object vrPlayer = vmcIt$vrPlayerGet.invoke(null);
            if (vrPlayer == null) return;
            Object pre = vmcIt$preField.get(vrPlayer);
            if (pre == null) return;
            if (vmcIt$originField == null) {
                vmcIt$originField = pre.getClass().getField("origin");
            }
            Object originObj = vmcIt$originField.get(pre);
            if (originObj instanceof Vec3 origin) {
                vmcIt$preData = pre;
                vmcIt$saved   = origin;
                vmcIt$originField.set(pre, origin.add(0.0, -drop, 0.0));
            }
        } catch (Throwable t) {
            vmcIt$broken  = true;
            vmcIt$preData = null;
            vmcIt$saved   = null;
        }
    }

    @Inject(method = "doProcess(Lnet/minecraft/class_746;)V", at = @At("RETURN"), require = 0, remap = false)
    private void vmcIt$restoreAfterInteract(LocalPlayer player, CallbackInfo ci) {
        if (vmcIt$preData == null || vmcIt$saved == null) return;
        try {
            vmcIt$originField.set(vmcIt$preData, vmcIt$saved);
        } catch (Throwable t) {
            vmcIt$broken = true;
        } finally {
            vmcIt$preData = null;
            vmcIt$saved   = null;
        }
    }
}
