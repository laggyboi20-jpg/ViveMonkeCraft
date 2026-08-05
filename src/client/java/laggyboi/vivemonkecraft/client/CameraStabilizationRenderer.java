package laggyboi.vivemonkecraft.client;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

// =====================================================================
// CAMERA STABILIZATION RENDERER  (QuestCraft / Vivecraft)
// =====================================================================
//
// Draws a black vignette border inside the VR headset display to narrow the
// perceived field-of-view while locomotion is fast, reducing motion sickness.
//
// ⚠️ 1.21.5 PORT — TEMPORARILY STUBBED ⚠️
// Minecraft 1.21.5 removed the immediate-mode render path this used
// (BufferUploader, CoreShaders, RenderSystem.setShader/enableBlend/
// disableDepthTest/defaultBlendFunc) in favour of the new RenderPipeline /
// GpuDevice command system. The head-locked vignette needs reimplementing
// against that API. The speed/easing logic below is preserved and still
// computes smoothFactor; only the actual GPU draw (drawVignette) is a no-op,
// so the rest of the mod builds and runs. Re-enable by implementing
// drawVignette() with a RenderPipeline. (This is the recurring pain file when
// porting up MC versions — the rest of the mod is render-API-light.)
// =====================================================================

public final class CameraStabilizationRenderer {

    private CameraStabilizationRenderer() {}

    // Locomotion speed (blocks/tick) at which the vignette starts appearing.
    private static final float SPEED_MIN = 0.12f;
    // Speed at which the vignette reaches full configured strength.
    private static final float SPEED_MAX = 0.55f;

    // Smoothed 0..1 factor persisted between render frames for easing.
    private static float smoothFactor = 0.0f;

    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(CameraStabilizationRenderer::onWorldRender);
    }

    private static void onWorldRender(WorldRenderContext ctx) {
        if (!VivemonkecraftClient.isEnabled()) return;
        if (!MovementConfig.cameraStabEnabled) return;

        // Gate: only draw when QuestCraft VR is actually active.
        // VivecraftBridge.isVrActive() uses reflection and never throws;
        // returns false when QuestCraft is absent or VR is off.
        if (!VivecraftBridge.isVrActive()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Horizontal speed drives the effect; vertical weight is halved so
        // normal gravity falls don't trigger it.
        Vec3 vel = mc.player.getDeltaMovement();
        double speed = vel.horizontalDistance() + Math.abs(vel.y) * 0.5;

        float strength  = (float) Math.max(0.0, Math.min(1.0, MovementConfig.cameraStabStrength));
        float rawFactor = (float) Math.max(0.0,
                Math.min(1.0, (speed - SPEED_MIN) / (SPEED_MAX - SPEED_MIN)));

        // Ease in fast (snap on within ~2 frames), ease out slowly (~15 frames).
        // VR runs at ~90 fps; at 0.5 step the vignette appears in 2 frames (~22ms),
        // and fades over ~15 frames (~170ms) after stopping — long enough not to flicker.
        if (rawFactor > smoothFactor) {
            smoothFactor += (rawFactor - smoothFactor) * 0.5f;
        } else {
            smoothFactor += (rawFactor - smoothFactor) * 0.07f;
        }

        if (smoothFactor < 0.01f) return;

        // edgeFrac: fraction of the half-screen (0..1) to cover from each edge.
        // 0.28 = up to 28% of the view from each edge at max strength.
        float edgeFrac = smoothFactor * strength * 0.28f;
        if (edgeFrac < 0.005f) return;

        // Alpha capped at 230 so the corners are never fully opaque —
        // you can still orient yourself even at max strength.
        int alpha = Math.min(230, (int)(230 * smoothFactor * strength));

        drawVignette(edgeFrac, alpha);
    }

    // Renders 4 black quads around the screen edges in clip space.
    // Temporarily replaces both matrices with identity so NDC coordinates
    // map 1:1 to screen position (-1 .. +1), then restores them.
    private static void drawVignette(float edgeFrac, int alpha) {
        // --- save ---
        Matrix4f      savedProj = new Matrix4f(RenderSystem.getProjectionMatrix());
        ProjectionType savedType = RenderSystem.getProjectionType();
        Matrix4fStack  mvStack   = RenderSystem.getModelViewStack();
        mvStack.pushMatrix();
        mvStack.identity(); // clear camera transform — vertices ARE clip cords now

        RenderSystem.setProjectionMatrix(new Matrix4f(), ProjectionType.ORTHOGRAPHIC);

        // --- draw ---
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(CoreShaders.POSITION_COLOR);

        // In NDC space_x/y span from -1 (left/bottom) to +1 (right/top).
        // e = edgeFrac * 2 converts 0..1 fraction of half-screen to NDC units.
        float e = edgeFrac * 2.0f;

        Tesselator   tess = Tesselator.getInstance();
        BufferBuilder buf  = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        // Top strip   — full width, top e of screen
        buf.addVertex(-1f,   1f,      0f).setColor(0, 0, 0, alpha);
        buf.addVertex( 1f,   1f,      0f).setColor(0, 0, 0, alpha);
        buf.addVertex( 1f,   1f - e,  0f).setColor(0, 0, 0, alpha);
        buf.addVertex(-1f,   1f - e,  0f).setColor(0, 0, 0, alpha);

        // Bottom strip — full width, bottom e of screen
        buf.addVertex(-1f,  -1f + e,  0f).setColor(0, 0, 0, alpha);
        buf.addVertex( 1f,  -1f + e,  0f).setColor(0, 0, 0, alpha);
        buf.addVertex( 1f,  -1f,      0f).setColor(0, 0, 0, alpha);
        buf.addVertex(-1f,  -1f,      0f).setColor(0, 0, 0, alpha);

        // Left strip  — left e, between top/bottom strips (no double-covering corners)
        buf.addVertex(-1f,      1f - e,  0f).setColor(0, 0, 0, alpha);
        buf.addVertex(-1f + e,  1f - e,  0f).setColor(0, 0, 0, alpha);
        buf.addVertex(-1f + e, -1f + e,  0f).setColor(0, 0, 0, alpha);
        buf.addVertex(-1f,     -1f + e,  0f).setColor(0, 0, 0, alpha);

        // Right strip — right e, between top/bottom strips
        buf.addVertex( 1f - e,  1f - e,  0f).setColor(0, 0, 0, alpha);
        buf.addVertex( 1f,      1f - e,  0f).setColor(0, 0, 0, alpha);
        buf.addVertex( 1f,     -1f + e,  0f).setColor(0, 0, 0, alpha);
        buf.addVertex( 1f - e, -1f + e,  0f).setColor(0, 0, 0, alpha);

        MeshData mesh = buf.buildOrThrow();
        BufferUploader.drawWithShader(mesh);

        // --- restore ---
        RenderSystem.enableDepthTest();
        RenderSystem.setProjectionMatrix(savedProj, savedType);
        mvStack.popMatrix();
    }
}
