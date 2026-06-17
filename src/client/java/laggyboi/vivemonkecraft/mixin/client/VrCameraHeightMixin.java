package laggyboi.vivemonkecraft.mixin.client;

import laggyboi.vivemonkecraft.client.MovementConfig;
import laggyboi.vivemonkecraft.client.VivemonkecraftClient;
import laggyboi.vivemonkecraft.client.VrHandClamp;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

// =====================================================================
// VR RENDER TWEAKS (mixin) — camera height offset + hand-model clamping
// =====================================================================
//
// Vivecraft's VRPlayer rebuilds its render snapshot (vrdata_world_render)
// every frame in preRender(). Every device pose — head AND both hands —
// computes its world position live as:
//     origin + (roomPos × worldScale).rotateY(rotation)
// reading the snapshot's `origin` field each call. Right after preRender()
// builds the snapshot, this mixin applies two cosmetic adjustments:
//
//  1. CAMERA HEIGHT OFFSET — shift origin down by cameraHeightOffset, which
//     lowers the camera and the hand models TOGETHER (they share the origin)
//     without shrinking anything (that would need worldScale).
//
//  2. HAND-MODEL CLAMP — while a hand grips, swap its pose (c0 / c1) for an
//     identical one whose position is the SURFACE grab point published by the
//     physics handler (VrHandClamp). The rendered hand then sits planted on
//     the block face like Gorilla Tag's hand followers instead of sinking
//     inside. The RAW tracked position is stashed first — VivecraftBridge
//     returns the stash while a clamp is active, so physics always sees the
//     real hand (the GT anchor servo NEEDS surface penetration).
//
// WHY A REFLECTION MIXIN (not @Shadow): the mod has no Vivecraft compile
// dependency — it talks to QuestCraft purely by reflection so it builds and
// degrades gracefully without Vivecraft on the classpath. This mixin keeps
// that contract: it targets VRPlayer by NAME and pokes fields by reflection,
// referencing no Vivecraft types. require = 0 + fail-flags mean that if the
// internals differ on some Vivecraft version, each feature just no-ops.
//
// preRender(float) runs ONCE per frame (it builds the snapshot shared by both
// eyes), so everything here is applied exactly once per frame.
// =====================================================================

@Mixin(targets = "org.vivecraft.client_vr.gameplay.VRPlayer", remap = false)
public class VrCameraHeightMixin {

    // -- camera height handles --
    private static Field vmc$renderField;   // VRPlayer.vrdata_world_render
    private static Field vmc$originField;   // VRData.origin
    private static boolean vmc$heightBroken = false;

    // -- hand clamp handles --
    private static Field vmc$c0Field;          // VRData.c0 (main hand)
    private static Field vmc$c1Field;          // VRData.c1 (off hand)
    private static Field vmc$poseDataField;    // VRDevicePose.data   (private)
    private static Field vmc$poseMatrixField;  // VRDevicePose.matrix (private)
    private static Field vmc$poseDirField;     // VRDevicePose.dir    (private)
    private static Method vmc$getPosition;     // VRDevicePose.getPosition()
    private static Method vmc$worldToRoom;     // VRPlayer.worldToRoomPos(Vec3, VRData)
    private static Constructor<?> vmc$poseCtor;
    private static boolean vmc$clampBroken = false;

    @Inject(method = "preRender(F)V", at = @At("RETURN"), require = 0, remap = false)
    private void vmc$renderTweaks(float partialTick, CallbackInfo ci) {
        Object vrData;
        try {
            if (vmc$renderField == null) {
                vmc$renderField = this.getClass().getField("vrdata_world_render");
            }
            vrData = vmc$renderField.get(this);
        } catch (Throwable t) {
            vmc$heightBroken = true;
            vmc$clampBroken  = true;
            return;
        }
        if (vrData == null) return;

        // Everything below only applies while gorilla locomotion is ON — toggling
        // the mod off restores the normal camera height and hands instantly.
        boolean locomotionOn = VivemonkecraftClient.isEnabled();

        // ---- 1. camera height offset (must run BEFORE the hand clamp so the
        //         world→room conversion uses the final origin) ----
        // REAL MONKE drops the view ~0.9 so your eyes sit inside a 1-block tunnel
        // and — crucially — your hand hitboxes line up with your REAL hands (the
        // authentic Gorilla Tag feel). This shift also drags your own VR BODY model
        // toward the floor (it's anchored to your feet), so PlayerModelMixin HIDES
        // that own first-person body whenever the view is dropped — you don't see a
        // body in GT anyway, and other players are unaffected (they render you from
        // the server position, not this local shift).
        double drop = vmc$currentDrop();
        if (drop > 0.0 && !vmc$heightBroken) {
            try {
                if (vmc$originField == null) {
                    vmc$originField = vrData.getClass().getField("origin");
                }
                Object originObj = vmc$originField.get(vrData);
                if (originObj instanceof Vec3 origin) {
                    vmc$originField.set(vrData, origin.add(0.0, -drop, 0.0));
                }
            } catch (Throwable t) {
                vmc$heightBroken = true;
            }
        }

        // ---- 2. hand-model clamp ----
        if (vmc$clampBroken || !locomotionOn) return;
        try {
            if (vmc$c0Field == null) {
                Class<?> vrDataCls = vrData.getClass();
                vmc$c0Field = vrDataCls.getField("c0");
                vmc$c1Field = vrDataCls.getField("c1");
                Class<?> poseCls = vmc$c0Field.getType();
                vmc$poseDataField   = poseCls.getDeclaredField("data");
                vmc$poseMatrixField = poseCls.getDeclaredField("matrix");
                vmc$poseDirField    = poseCls.getDeclaredField("dir");
                vmc$poseDataField.setAccessible(true);
                vmc$poseMatrixField.setAccessible(true);
                vmc$poseDirField.setAccessible(true);
                vmc$getPosition = poseCls.getMethod("getPosition");
                vmc$worldToRoom = this.getClass().getMethod("worldToRoomPos", Vec3.class, vrDataCls);
                vmc$poseCtor    = poseCls.getConstructor(
                        vrDataCls, vrDataCls, Matrix4fc.class, Vector3fc.class, Vector3fc.class);
            }
            vmc$clampHand(vrData, vmc$c0Field, VrHandClamp.clampMain, true);
            vmc$clampHand(vrData, vmc$c1Field, VrHandClamp.clampOff,  false);
        } catch (Throwable t) {
            // Unknown Vivecraft layout — disable permanently so we never spam or crash.
            vmc$clampBroken = true;
            VrHandClamp.rawMain = null;
            VrHandClamp.rawOff  = null;
        }
    }

    // How far the VR view is currently dropped (blocks). 0 = no drop.
    @org.spongepowered.asm.mixin.Unique
    private static double vmc$currentDrop() {
        if (!VivemonkecraftClient.isEnabled()) return 0.0;
        // No drop while riding (boat/minecart/horse) or elytra-flying: the seat/flight
        // already sets your eye height, so dropping it 0.9 would sink the camera down
        // INTO the vehicle. Locomotion is suspended in those states anyway.
        net.minecraft.client.player.LocalPlayer p = net.minecraft.client.Minecraft.getInstance().player;
        if (p != null && (p.isPassenger() || p.isFallFlying())) return 0.0;
        return MovementConfig.realMonke ? 0.9 : MovementConfig.cameraHeightOffset;
    }

    // NOTE: an earlier attempt also lowered the GAMEPLAY snapshots (vrdata_world_pre
    // / _post) to make roomscale block-breaking match the lowered view. It shoved the
    // player ~0.9 into the floor (Vivecraft repositions the body to those snapshots),
    // so it was reverted. The block-break-vs-view mismatch is left unfixed for now.

    // Stashes the hand's raw tracked world position, then (if a clamp point is
    // active) replaces the pose with an identical one positioned at the surface.
    // Orientation passes through LIVE — the planted hand still rotates freely
    // with the controller; only its position is pinned to the block face.
    private void vmc$clampHand(Object vrData, Field handField, Vec3 clampPoint, boolean main)
            throws ReflectiveOperationException {
        Object pose = handField.get(vrData);
        if (pose == null) return;

        Vec3 rawWorld = (Vec3) vmc$getPosition.invoke(pose);
        if (main) VrHandClamp.rawMain = rawWorld;
        else      VrHandClamp.rawOff  = rawWorld;

        if (clampPoint == null) return;

        Object roomPos = vmc$worldToRoom.invoke(null, clampPoint, vrData);
        Object planted = vmc$poseCtor.newInstance(
                vrData,
                vmc$poseDataField.get(pose),
                vmc$poseMatrixField.get(pose),
                roomPos,
                vmc$poseDirField.get(pose));
        handField.set(vrData, planted);
    }
}
