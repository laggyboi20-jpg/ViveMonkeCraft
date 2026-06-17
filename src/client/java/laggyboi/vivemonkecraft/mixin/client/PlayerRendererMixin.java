package laggyboi.vivemonkecraft.mixin.client;

import laggyboi.vivemonkecraft.client.MonkeModelClientSet;
import laggyboi.vivemonkecraft.client.MovementConfig;
import laggyboi.vivemonkecraft.client.VivemonkecraftClient;
import laggyboi.vivemonkecraft.client.VmcMonkeRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Marks each player's render state with "draw as monke?" while the renderer
// still knows WHICH player is being drawn (the model only sees the state).
// The set is fed by monke-server broadcasts + the local player's own toggle.
@Mixin(AvatarRenderer.class)
public class PlayerRendererMixin {

    // 1.21.9 renamed PlayerRenderer -> AvatarRenderer and PlayerRenderState ->
    // AvatarRenderState, and extractRenderState's first param is now the new
    // net.minecraft.world.entity.Avatar superclass (Player extends Avatar).
    @Inject(
        method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
        at = @At("TAIL"),
        require = 0
    )
    private void vmc$markMonke(Avatar player, AvatarRenderState state,
                                float partialTick, CallbackInfo ci) {
        VmcMonkeRenderState s = (VmcMonkeRenderState) state;
        s.vmc$setMonke(MonkeModelClientSet.isMonke(player.getUUID()));

        // Hide ONLY the local player's own body while the VR view is dropped — that
        // shift drags this body model to the floor. Other players are never flagged.
        boolean viewDropped = VivemonkecraftClient.isEnabled()
                && (MovementConfig.realMonke || MovementConfig.cameraHeightOffset > 0.0);
        boolean isLocal = player == Minecraft.getInstance().player;
        s.vmc$setHideOwnBody(viewDropped && isLocal);
    }
}
