package laggyboi.vivemonkecraft.client;

import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Thin, dependency-free adapter over Vivecraft's VR API.
 *
 * <p>Targets <b>Vivecraft 1.2.x / QuestCraft (QCXR)</b>, which uses the internal
 * {@code org.vivecraft.client_vr.*} package (no public API).
 *
 * <p>Reflection is used so Vivecraft does NOT need to be a compile-time dependency —
 * the mod compiles without Vivecraft on the classpath and falls back gracefully to
 * {@code MISSING} if QuestCraft is not installed.
 *
 * <p>Fields accessed via reflection:
 * <ul>
 *   <li>{@code VRState.VR_RUNNING}          — static boolean, true while VR is active
 *   <li>{@code VRPlayer.get()}              — static factory → VRPlayer instance
 *   <li>{@code VRPlayer#vrdata_world_render}— VRData snapshot used for rendering
 *   <li>{@code VRData#hmd / c0 / c1}        — head / main hand / off-hand device poses
 *   <li>{@code VRDevicePose#getPosition()}  — world-space Vec3 position
 * </ul>
 */
public final class VivecraftBridge {

    // -----------------------------------------------------------------------
    // Static singleton — lets renderer classes call VivecraftBridge.isVrActive()
    // without needing their own instance.
    // -----------------------------------------------------------------------

    private static final VivecraftBridge INSTANCE = new VivecraftBridge();

    /**
     * Returns {@code true} when QuestCraft / Vivecraft 1.2.x is present AND
     * VR is currently active.  Safe to call from any thread; never throws.
     */
    public static boolean isVrActive() {
        return INSTANCE.isVRActive();
    }

    /**
     * Enable/disable Vivecraft's teleport movement at runtime (not a saved setting).
     * We disable it while gorilla locomotion is active so the teleport button can't
     * desync the room origin and break our physics. No-op if Vivecraft is absent.
     */
    public static void setTeleportDisabled(boolean disabled) {
        // POLARITY: the debug read-back proved teleportOverride is effectively "teleport
        // ENABLED" — isTeleportEnabled() tracks it (override=true → on, false → off).
        // So to DISABLE teleport we must pass FALSE, and to allow it, TRUE.
        INSTANCE.setTeleportOverride(!disabled);
    }

    // -----------------------------------------------------------------------
    // Connection lifecycle
    // -----------------------------------------------------------------------

    private enum Link { UNTRIED, LEGACY, MISSING }
    private Link link = Link.UNTRIED;

    // -----------------------------------------------------------------------
    // Legacy API handles  (Vivecraft 1.2.x / QuestCraft)
    // -----------------------------------------------------------------------

    private Field  fVrRunning;     // VRState.VR_RUNNING  (static boolean)
    private Method mVrPlayerGet;   // VRPlayer.get()      (static → VRPlayer)
    private Field  fVrDataRender;  // VRPlayer#vrdata_world_render (VRData)
    private Field  fHmd;           // VRData#hmd          (VRDevicePose = head)
    private Field  fC0;            // VRData#c0           (VRDevicePose = main hand)
    private Field  fC1;            // VRData#c1           (VRDevicePose = off hand)
    private Method mGetPosition;   // VRDevicePose#getPosition() → Vec3

    // -----------------------------------------------------------------------
    // Public accessors
    // -----------------------------------------------------------------------

    /** @return true only when QuestCraft is present AND VR is currently active. */
    public boolean isVRActive() {
        if (tryWire() == Link.LEGACY) return legacyIsVRActive();
        return false;
    }

    /** World-space position of the right/main-hand controller, or null. */
    public Vec3 getMainHandPos() {
        // While the hand-model clamp is active, the c0 pose has been swapped to the
        // SURFACE point for rendering — physics must keep seeing the real hand, so
        // return the raw position stashed by the render mixin before the swap.
        // (The GT anchor servo NEEDS surface penetration; the clamp is cosmetic.)
        if (VrHandClamp.clampMain != null && VrHandClamp.rawMain != null) {
            return VrHandClamp.rawMain;
        }
        if (tryWire() == Link.LEGACY) return legacyPosition(fC0);
        return null;
    }

    /** World-space position of the left/off-hand controller, or null. */
    public Vec3 getOffHandPos() {
        if (VrHandClamp.clampOff != null && VrHandClamp.rawOff != null) {
            return VrHandClamp.rawOff;
        }
        if (tryWire() == Link.LEGACY) return legacyPosition(fC1);
        return null;
    }

    /** World-space position of the headset/eyes, or null. */
    public Vec3 getHeadPos() {
        if (tryWire() == Link.LEGACY) return legacyPosition(fHmd);
        return null;
    }

    // -----------------------------------------------------------------------
    // Teleport-aim detection (wired separately so a failure here never disables
    // the core position API above).
    // -----------------------------------------------------------------------

    private boolean tpTried  = false;
    private boolean tpBroken = false;
    private Method mDataHolderGetInstance; // ClientDataHolderVR.getInstance()
    private Field  fTeleportTracker;       // ClientDataHolderVR#teleportTracker
    private Method mIsAiming;              // TeleportTracker#isAiming() → boolean

    /**
     * True while the player is holding the teleport button (Vivecraft's teleport-aim
     * mode). During it Vivecraft freezes the hand/room pose, so our locomotion would
     * anchor to a stale hand and slide in place — the caller should go inert. Never
     * throws; returns false if Vivecraft isn't present or the internals differ.
     */
    public boolean isTeleportAiming() {
        if (tpBroken) return false;
        try {
            if (!tpTried) {
                tpTried = true;
                Class<?> dh = Class.forName("org.vivecraft.client_vr.ClientDataHolderVR");
                mDataHolderGetInstance = dh.getMethod("getInstance");
                fTeleportTracker       = dh.getField("teleportTracker");
                Class<?> tt = Class.forName("org.vivecraft.client_vr.gameplay.trackers.TeleportTracker");
                mIsAiming = tt.getMethod("isAiming");
                VmcDebugLog.log("isTeleportAiming wired OK");
            }
            Object holder = mDataHolderGetInstance.invoke(null);
            if (holder == null) return false;
            Object tracker = fTeleportTracker.get(holder);
            if (tracker == null) return false;
            Object aiming = mIsAiming.invoke(tracker);
            return (aiming instanceof Boolean) && (Boolean) aiming;
        } catch (Throwable t) {
            tpBroken = true;
            VmcDebugLog.log("isTeleportAiming FAILED: " + t);
            return false;
        }
    }

    private boolean snapTried  = false;
    private boolean snapBroken = false;
    private Method  mSnapOrigin; // VRPlayer#snapRoomOriginToPlayerEntity(Entity,boolean,boolean)

    /**
     * Force Vivecraft's room origin to re-sync to the player entity — the fix for a
     * teleport leaving the origin behind (you slide in place / can't push afterwards).
     * Args (false, true) mirror Vivecraft's own JumpTracker re-sync. Call ONLY right
     * after a teleport, never every tick — it cancels roomscale walking otherwise.
     */
    public void snapRoomOriginToPlayer(net.minecraft.world.entity.Entity player) {
        if (snapBroken || player == null || tryWire() != Link.LEGACY) return;
        try {
            if (!snapTried) {
                snapTried = true;
                Class<?> vrPlayer = Class.forName("org.vivecraft.client_vr.gameplay.VRPlayer");
                mSnapOrigin = vrPlayer.getMethod("snapRoomOriginToPlayerEntity",
                        net.minecraft.world.entity.Entity.class, boolean.class, boolean.class);
                VmcDebugLog.log("snapRoomOriginToPlayer wired OK");
            }
            Object vp = mVrPlayerGet.invoke(null); // static VRPlayer.get()
            if (vp != null) mSnapOrigin.invoke(vp, player, false, true);
            else VmcDebugLog.log("snapRoomOriginToPlayer: VRPlayer.get() returned null");
        } catch (Throwable t) {
            snapBroken = true;
            VmcDebugLog.log("snapRoomOriginToPlayer FAILED: " + t);
        }
    }

    private boolean tpoTried  = false;
    private boolean tpoBroken = false;
    private Method  mSetTeleportOverride; // VRPlayer#setTeleportOverride(boolean)

    /**
     * Set Vivecraft's teleport override and IMMEDIATELY re-apply it. Setting the flag
     * alone does nothing live — Vivecraft only pushes isTeleportEnabled() onto the
     * teleport input actions inside updateTeleportKeys(), which normally runs only at
     * init/settings-change. updateTeleportKeys() just calls setEnabled() on the two
     * teleport actions (no side effects), so we call it here to make the change stick.
     */
    public void setTeleportOverride(boolean override) {
        if (tpoBroken || tryWire() != Link.LEGACY) return;
        try {
            if (!tpoTried) {
                tpoTried = true;
                Class<?> vrPlayer = Class.forName("org.vivecraft.client_vr.gameplay.VRPlayer");
                mSetTeleportOverride = vrPlayer.getMethod("setTeleportOverride", boolean.class);
            }
            Object vp = mVrPlayerGet.invoke(null); // static VRPlayer.get()
            if (vp != null) mSetTeleportOverride.invoke(vp, override);
        } catch (Throwable t) {
            tpoBroken = true;
        }
    }

    // -----------------------------------------------------------------------
    // Wire-up — idempotent after first attempt
    // -----------------------------------------------------------------------

    private Link tryWire() {
        if (link != Link.UNTRIED) return link;
        link = wireLegacy();
        return link;
    }

    // -----------------------------------------------------------------------
    // Legacy wiring (1.2.x — QuestCraft / QCXR)
    // -----------------------------------------------------------------------

    private Link wireLegacy() {
        try {
            Class<?> vrStateType      = Class.forName("org.vivecraft.client_vr.VRState");
            Class<?> vrPlayerType     = Class.forName("org.vivecraft.client_vr.gameplay.VRPlayer");
            Class<?> vrDataType       = Class.forName("org.vivecraft.client_vr.VRData");
            Class<?> vrDevicePoseType = Class.forName("org.vivecraft.client_vr.VRData$VRDevicePose");

            fVrRunning    = vrStateType.getField("VR_RUNNING");
            mVrPlayerGet  = vrPlayerType.getMethod("get");
            fVrDataRender = vrPlayerType.getField("vrdata_world_render");
            fHmd          = vrDataType.getField("hmd");
            fC0           = vrDataType.getField("c0");
            fC1           = vrDataType.getField("c1");
            mGetPosition  = vrDevicePoseType.getMethod("getPosition");

            System.out.println("[ViveMonkeCraft] QuestCraft API connected (Vivecraft 1.2.x / QCXR)");
            return Link.LEGACY;

        } catch (ClassNotFoundException e) {
            System.out.println("[ViveMonkeCraft] QuestCraft not found — mod requires QuestCraft / Vivecraft 1.2.x.");
            return Link.MISSING;
        } catch (ReflectiveOperationException e) {
            System.out.println("[ViveMonkeCraft] QuestCraft API present but unusable: " + e);
            return Link.MISSING;
        }
    }

    // -----------------------------------------------------------------------
    // Legacy data accessors
    // -----------------------------------------------------------------------

    private boolean legacyIsVRActive() {
        try {
            return fVrRunning.getBoolean(null); // static field
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    private Vec3 legacyPosition(Field devicePoseField) {
        try {
            Object vrPlayer   = mVrPlayerGet.invoke(null); // static
            if (vrPlayer == null) return null;
            Object vrData     = fVrDataRender.get(vrPlayer);
            if (vrData == null) return null;
            Object devicePose = devicePoseField.get(vrData);
            if (devicePose == null) return null;
            Object pos        = mGetPosition.invoke(devicePose);
            return (pos instanceof Vec3) ? (Vec3) pos : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
