package laggyboi.vivemonkecraft.client;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.CoreShaders;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

// =====================================================================
// CAMERA STABILIZATION RENDERER  (QuestCraft / Vivecraft 1.2.x)
// =====================================================================
//
// Draws a black vignette border inside the VR headset display to narrow
// the perceived field-of-view while locomotion is fast. This reduces the
// peripheral visual-motion signal that contributes to motion sickness.
//
// Why WorldRenderEvents.AFTER_TRANSLUCENT:
//   HudRenderCallback fires during Vivecraft floating GUI panel pass —
//   its output appears on that panel, not in the VR lens. AFTER_TRANSLUCENT
//   fires inside the actual scene render, so the vignette ends up in the
//   headset display.
//
// Why clip-space / identity matrices:
//   The vignette must be head-locked (no world-space parallax). Resetting
//   both ModelView and Projection to identity maps NDC cords (-1 - +1)
//   directly to screen edges, independent of the camera. Depth test off so
//   it draws on top of the scene.
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

    // TODO(1.21.5): reimplement with the new RenderPipeline / GpuDevice API.
    // Was: identity matrices + Tesselator QUADS + BufferUploader.drawWithShader
    // with CoreShaders.POSITION_COLOR. Currently a no-op so the mod builds on 1.21.5.
    private static void drawVignette(float edgeFrac, int alpha) {
        // intentionally empty until ported to the 1.21.5 render pipeline
    }
}
