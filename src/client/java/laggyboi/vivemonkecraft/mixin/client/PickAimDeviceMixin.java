package laggyboi.vivemonkecraft.mixin.client;

import laggyboi.vivemonkecraft.client.VivemonkecraftClient;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

// =====================================================================
// FREE-MOVE BLOCK-BREAK ALIGNMENT — look-aim the crosshair pick
// =====================================================================
//
// Free-move (joystick) breaking targets the block ~1 ABOVE what you look at when
// the view is dropped. Root cause: Vivecraft's crosshair pick uses
// vrdata_world_render.getAim(), which returns the CONTROLLER pose (c0) when the
// VRSettings "aim device" is CONTROLLER (the default). The controller naturally
// points above your downward gaze, so it picks the higher block.
//
// FIX: for the DURATION of GameRenderer.pick() only, force the aim device to HMD,
// then restore the player's real setting. getAim() then returns the head pose, so
// the crosshair (and the block that breaks, which follows mc.hitResult) lands where
// you LOOK. Scoped to the pick so:
//   * the user's saved Vivecraft setting is never persisted as changed (we restore
//     within the same frame, long before any settings save),
//   * roomscale hand interaction (InteractTracker, controller-based, runs outside
//     pick) is untouched.
// The HMD pose uses the head eye = the already-dropped view eye, so the ray starts
// at eye level (no "crosshair sinks under the model" like the eye-shift attempt).
//
// Reflection-only (no Vivecraft compile dependency); a fail flag makes it no-op if
// the internals differ on some Vivecraft version.
// =====================================================================
@Mixin(GameRenderer.class)
public class PickAimDeviceMixin {

    @Unique private static Method vmcAim$getInstance;   // ClientDataHolderVR.getInstance()
    @Unique private static Field  vmcAim$settingsField; // ClientDataHolderVR.vrSettings
    @Unique private static Field  vmcAim$aimField;      // VRSettings.aimDevice
    @Unique private static Object vmcAim$hmd;           // AimDevice.HMD
    @Unique private static boolean vmcAim$broken = false;

    // Restored at RETURN. Non-null only while we actually swapped this frame.
    @Unique private static Object vmcAim$settings = null;
    @Unique private static Object vmcAim$saved    = null;

    @Inject(method = "pick(F)V", at = @At("HEAD"))
    private void vmcAim$lookAimStart(float partialTick, CallbackInfo ci) {
        vmcAim$settings = null;
        vmcAim$saved    = null;
        if (vmcAim$broken || !VivemonkecraftClient.isEnabled()) return;

        try {
            if (vmcAim$getInstance == null) {
                Class<?> dh = Class.forName("org.vivecraft.client_vr.ClientDataHolderVR");
                vmcAim$getInstance  = dh.getMethod("getInstance");
                vmcAim$settingsField = dh.getField("vrSettings");
            }
            Object holder = vmcAim$getInstance.invoke(null);
            if (holder == null) return;
            Object settings = vmcAim$settingsField.get(holder);
            if (settings == null) return;
            if (vmcAim$aimField == null) {
                vmcAim$aimField = settings.getClass().getField("aimDevice");
                @SuppressWarnings({"unchecked", "rawtypes"})
                Object hmd = Enum.valueOf((Class) vmcAim$aimField.getType(), "HMD");
                vmcAim$hmd = hmd;
            }
            Object current = vmcAim$aimField.get(settings);
            if (current != vmcAim$hmd) {
                vmcAim$settings = settings;
                vmcAim$saved    = current;
                vmcAim$aimField.set(settings, vmcAim$hmd);
            }
        } catch (Throwable t) {
            vmcAim$broken   = true;
            vmcAim$settings = null;
            vmcAim$saved    = null;
        }
    }

    @Inject(method = "pick(F)V", at = @At("RETURN"))
    private void vmcAim$lookAimRestore(float partialTick, CallbackInfo ci) {
        if (vmcAim$settings == null || vmcAim$saved == null) return;
        try {
            vmcAim$aimField.set(vmcAim$settings, vmcAim$saved);
        } catch (Throwable t) {
            vmcAim$broken = true;
        } finally {
            vmcAim$settings = null;
            vmcAim$saved    = null;
        }
    }
}
