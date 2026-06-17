package laggyboi.vivemonkecraft.mixin.client;

import laggyboi.vivemonkecraft.client.VmcMonkeRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

// Stitches the monke-model flag onto vanilla's AvatarRenderState (1.21.9 renamed
// PlayerRenderState -> AvatarRenderState) so the renderer (which knows the
// player) can hand it to the model (which doesn't).
@Mixin(AvatarRenderState.class)
public class PlayerRenderStateMixin implements VmcMonkeRenderState {

    @Unique
    private boolean vmc$monke = false;

    @Unique
    private boolean vmc$hideOwnBody = false;

    @Override
    public boolean vmc$isMonke() {
        return vmc$monke;
    }

    @Override
    public void vmc$setMonke(boolean monke) {
        vmc$monke = monke;
    }

    @Override
    public boolean vmc$hideOwnBody() {
        return vmc$hideOwnBody;
    }

    @Override
    public void vmc$setHideOwnBody(boolean hide) {
        vmc$hideOwnBody = hide;
    }
}
