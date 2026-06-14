package laggyboi.vivemonkecraft.mixin.client;

import laggyboi.vivemonkecraft.client.MonkeModelClientSet;
import laggyboi.vivemonkecraft.client.MovementConfig;
import laggyboi.vivemonkecraft.client.VivemonkecraftClient;
import laggyboi.vivemonkecraft.client.VmcMonkeRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Marks each player's render state with "draw as monke?" while the renderer
// still knows WHICH player is being drawn (the model only sees the state).
// The set is fed by monke-server broadcasts + the local player's own toggle.
@Mixin(PlayerRenderer.class)
public class PlayerRendererMixin {

    @Inject(
        method = "extractRenderState(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;F)V",
        at = @At("TAIL"),
        require = 0
    )
    private void vmc$markMonke(AbstractClientPlayer player, PlayerRenderState state,
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
