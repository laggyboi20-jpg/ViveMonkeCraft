package laggyboi.vivemonkecraft.client;

import net.minecraft.world.phys.Vec3;

// =====================================================================
// HAND MARKER RENDERER
// =====================================================================
//
// Per-tick state for the hand markers (shoulder→grab arm + grab-point cube),
// written once per game tick by GorillaLocomotionHandler.
//
// ⚠️ DRAWING IS CURRENTLY DISABLED ⚠️
// The in-world line drawing relied on Fabric's WorldRenderEvents /
// WorldRenderContext, which was removed in the 1.21.9 render rework. The new
// pipeline (and 26.2's GUI/HUD refactor) also removed MultiBufferSource, so the
// old immediate-mode line draw can't be ported as-is. The state below is still
// maintained so a future marker implementation (planned: client-side particles,
// which are render-API-stable across versions) can read it without touching the
// physics. Until then register() is a no-op and nothing is drawn.
// =====================================================================

public final class HandMarkerRenderer {

    private HandMarkerRenderer() {}

    // =========================================================================
    // Tick-to-frame state — written by GorillaLocomotionHandler
    // =========================================================================

    // Shoulder joint positions (world-space), computed from headPositon + player yaw
    // each tick so they track the player model's arm sockets.
    public static Vec3    shoulderMain = null;
    public static Vec3    shoulderOff  = null;

    // Grab / touch points — where the hand hitbox currently lands.
    public static Vec3    grabMain     = null;
    public static Vec3    grabOff      = null;

    public static boolean grippingMain = false;
    public static boolean grippingOff  = false;

    // True while sliding down a wall — would tint BOTH markers yellow (overrides the
    // usual green/red), the visual cue that you're in a no-fall-damage slide.
    public static boolean sliding      = false;

    public static void clearState() {
        shoulderMain = shoulderOff = grabMain = grabOff = null;
        grippingMain = grippingOff = false;
        sliding = false;
    }

    // =========================================================================
    // Registration — call once from VivemonkecraftClient.onInitializeClient()
    // =========================================================================

    public static void register() {
        // No-op: the world-render hook this used (WorldRenderEvents) was removed in
        // 1.21.9, and 26.2 removed MultiBufferSource too. Re-enable by feeding the
        // state above into a client-side particle emitter (planned rebuild).
    }
}
