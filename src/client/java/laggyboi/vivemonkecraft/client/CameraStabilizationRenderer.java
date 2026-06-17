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
        if (!VivecraftBridge.isVrActive()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Horizontal speed drives the effect; vertical weight halved so normal falls
        // don't trigger it.
        Vec3 vel = mc.player.getDeltaMovement();
        double speed = vel.horizontalDistance() + Math.abs(vel.y) * 0.5;

        float strength  = (float) Math.max(0.0, Math.min(1.0, MovementConfig.cameraStabStrength));
        float rawFactor = (float) Math.max(0.0,
                Math.min(1.0, (speed - SPEED_MIN) / (SPEED_MAX - SPEED_MIN)));

        // Ease in fast, ease out slowly.
        if (rawFactor > smoothFactor) {
            smoothFactor += (rawFactor - smoothFactor) * 0.5f;
        } else {
            smoothFactor += (rawFactor - smoothFactor) * 0.07f;
        }
        if (smoothFactor < 0.01f) return;

        float edgeFrac = smoothFactor * strength * 0.28f;
        if (edgeFrac < 0.005f) return;

        int alpha = Math.min(230, (int) (230 * smoothFactor * strength));
        drawVignette(edgeFrac, alpha);
    }

    // TODO(1.21.5): reimplement with the new RenderPipeline / GpuDevice API.
    // Was: identity matrices + Tesselator QUADS + BufferUploader.drawWithShader
    // with CoreShaders.POSITION_COLOR. Currently a no-op so the mod builds on 1.21.5.
    private static void drawVignette(float edgeFrac, int alpha) {
        // intentionally empty until ported to the 1.21.5 render pipeline
    }
}
