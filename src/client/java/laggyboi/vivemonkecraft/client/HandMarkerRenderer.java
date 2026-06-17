package laggyboi.vivemonkecraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

// =====================================================================
// HAND MARKER RENDERER
// =====================================================================
//
// Replaces the old particle hand markers with per-frame geometry drawn
// directly into the world render pipeline via WorldRenderEvents.
// Zero particles, zero entities — best performance for Quest standalone,
// and fully client-side so it works on any server.
//
// Draws an arm line per hand (shoulder → grab point) plus a wireframe cube at
// the grab point matching the hand hitbox size.
// Green when gripping a block, red when free. Lines only.
//
// State fields are written once per game tick by GorillaLocomotionHandler
// and read every render frame here.
// =====================================================================

public final class HandMarkerRenderer {

    private HandMarkerRenderer() {}

    // =========================================================================
    // Tick-to-frame state — written by GorillaLocomotionHandler, read per frame
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

    // True while sliding down a wall — tints BOTH markers yellow (overrides the
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
        // 1.21.9 removed Fabric's WorldRenderEvents/WorldRenderContext in the render
        // rework, so there's no per-frame world hook to register against anymore.
        // The drawing logic below is preserved; re-enable the markers by calling
        // renderMarkers(...) from a LevelRenderer mixin once a hook that exposes a
        // PoseStack + MultiBufferSource + camera is wired up for the new pipeline.
    }

    // =========================================================================
    // Render callback — call per frame with the world-render context's pose stack,
    // buffer source, and camera position. Currently unwired on 1.21.9 (see register).
    // =========================================================================

    public static void renderMarkers(PoseStack stack, MultiBufferSource buf, Vec3 cam) {
        if (!VivemonkecraftClient.isEnabled()) return;
        if (!MovementConfig.showHandMarkers) return;
        if (shoulderMain == null || grabMain == null || shoulderOff == null || grabOff == null) return;
        if (buf == null) return;

        VertexConsumer lines = buf.getBuffer(RenderType.lines());

        // Capped radius so the drawn cube always matches the EFFECTIVE grab box.
        double r = SettingCaps.handRadius();

        stack.pushPose();
        stack.translate(-cam.x, -cam.y, -cam.z);

        drawArm (lines, stack, shoulderMain, grabMain, grippingMain);
        drawArm (lines, stack, shoulderOff,  grabOff,  grippingOff);
        drawCube(lines, stack, grabMain, r, grippingMain);
        drawCube(lines, stack, grabOff,  r, grippingOff);

        stack.popPose();

        // Flush immediately so the lines are visible this frame.
        if (buf instanceof MultiBufferSource.BufferSource bs) {
            bs.endBatch(RenderType.lines());
        }
    }

    // =========================================================================
    // Arm drawing
    // =========================================================================

    // Draws a single straight line from shoulder socket to grab point.
    // Yellow while wall-sliding, otherwise green when gripping / red when free.
    private static void drawArm(VertexConsumer lines, PoseStack stack,
                                  Vec3 shoulder, Vec3 grabPoint, boolean gripping) {
        float r = sliding ? 1.0f : (gripping ? 0.0f : 1.0f);
        float g = sliding ? 1.0f : (gripping ? 1.0f : 0.0f);
        putLine(lines, stack.last().pose(),
                shoulder.x, shoulder.y, shoulder.z,
                grabPoint.x, grabPoint.y, grabPoint.z,
                r, g, 0f);
    }

    // Draws a wireframe cube centred on `center` with half-size `r` (matches the
    // hand hitbox radius so the cube shows exactly what the grab detection sees).
    // Same color scheme as the arm line: green = gripping, red = free.
    private static void drawCube(VertexConsumer lines, PoseStack stack,
                                   Vec3 centre, double r, boolean gripping) {
        float cr = sliding ? 1.0f : (gripping ? 0.0f : 1.0f);
        float cg = sliding ? 1.0f : (gripping ? 1.0f : 0.0f);
        Matrix4f m = stack.last().pose();

        double x0 = centre.x - r, x1 = centre.x + r;
        double y0 = centre.y - r, y1 = centre.y + r;
        double z0 = centre.z - r, z1 = centre.z + r;

        // Bottom face
        putLine(lines, m, x0,y0,z0, x1,y0,z0, cr,cg,0f);
        putLine(lines, m, x1,y0,z0, x1,y0,z1, cr,cg,0f);
        putLine(lines, m, x1,y0,z1, x0,y0,z1, cr,cg,0f);
        putLine(lines, m, x0,y0,z1, x0,y0,z0, cr,cg,0f);
        // Top face
        putLine(lines, m, x0,y1,z0, x1,y1,z0, cr,cg,0f);
        putLine(lines, m, x1,y1,z0, x1,y1,z1, cr,cg,0f);
        putLine(lines, m, x1,y1,z1, x0,y1,z1, cr,cg,0f);
        putLine(lines, m, x0,y1,z1, x0,y1,z0, cr,cg,0f);
        // Vertical edges
        putLine(lines, m, x0,y0,z0, x0,y1,z0, cr,cg,0f);
        putLine(lines, m, x1,y0,z0, x1,y1,z0, cr,cg,0f);
        putLine(lines, m, x1,y0,z1, x1,y1,z1, cr,cg,0f);
        putLine(lines, m, x0,y0,z1, x0,y1,z1, cr,cg,0f);
    }

    // =========================================================================
    // Geometry helpers
    // =========================================================================

    // Emits one line segment (A→B). Normal is auto-computed from the direction.
    // In 1.21.4, setNormal takes plain floats (the Matrix 3f overload was removed).
    private static void putLine(VertexConsumer v, Matrix4f m,
                                  double ax, double ay, double az,
                                  double bx, double by, double bz,
                                  float r, float g, float b) {
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float nx = len > 0 ? (float) (dx / len) : 1f;
        float ny = len > 0 ? (float) (dy / len) : 0f;
        float nz = len > 0 ? (float) (dz / len) : 0f;

        v.addVertex(m, (float) ax, (float) ay, (float) az)
         .setColor(r, g, b, 1f).setNormal(nx, ny, nz);
        v.addVertex(m, (float) bx, (float) by, (float) bz)
         .setColor(r, g, b, 1f).setNormal(nx, ny, nz);
    }

}
