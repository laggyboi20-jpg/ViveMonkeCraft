package laggyboi.vivemonkecraft.client;

import net.minecraft.world.phys.Vec3;

// =====================================================================
// VR HAND CLAMP — keeps the RENDERED Vivecraft hand models out of blocks
// =====================================================================
//
// Gorilla Tag's hands visually STOP at the surface they grab (the official
// Player.cs "hand followers") while the real tracked hand may push inside.
// We reproduce that: while a hand grips, the physics handler publishes the
// surface point here (clamp*), and a render-frame mixin swaps that hand's
// pose in Vivecraft's vrdata_world_render so the hand MODEL is drawn planted
// on the block face instead of sunk into it.
//
// CRITICAL CONTRACT: the mixin stashes the RAW tracked position (raw*) BEFORE
// swapping the pose, and VivecraftBridge returns the stash while a clamp is
// active — the physics must keep seeing the real hand (the GT anchor servo
// needs surface penetration; the visual clamp is cosmetic only).
//
// Written by the game-tick thread (clamp*) and the render thread (raw*);
// all fields volatile, reads tolerate one frame of staleness.
// =====================================================================
public final class VrHandClamp {

    /** Surface point the MAIN hand (Vivecraft c0) is gripping, or null = no clamp. */
    public static volatile Vec3 clampMain = null;
    /** Surface point the OFF hand (Vivecraft c1) is gripping, or null = no clamp. */
    public static volatile Vec3 clampOff  = null;

    /** Raw tracked world position of c0 this frame, stashed by the mixin BEFORE the swap. */
    public static volatile Vec3 rawMain = null;
    /** Raw tracked world position of c1 this frame, stashed by the mixin BEFORE the swap. */
    public static volatile Vec3 rawOff  = null;

    private VrHandClamp() {}

    /** Clears everything — call when grips reset (disable, teleport, desync). */
    public static void clear() {
        clampMain = null;
        clampOff  = null;
    }
}
