package laggyboi.vivemonkecraft.mixin.client;
import laggyboi.vivemonkecraft.client.MovementConfig;
import laggyboi.vivemonkecraft.client.VmcMonkeRenderState;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;






// =====================================================================
// MONKE MODEL (mixin) - the Gorilla Tag body: no legs, shorter torso
// =====================================================================
//
// Runs at the END of PlayerModel.setupAnim, after vanilla (and Vivecraft's
// super-call) has posed every part:
//   * MONKE MODEL (flagged players): legs hidden, torso/arm/head tuning.
//   * HIDE OWN HEAD (your own first-person body while the VR view is dropped):
//     hide ONLY the head + hat so you don't see inside your own head.
//
// Both run independently and the transforms always execute when monke is on,
// so torso rotation works even with the view dropped (an earlier version
// early-returned for "hide own body" and silently killed the transforms).
//
// priority 2000 so we apply after Vivecraft's own model adjustments.
// =====================================================================
@Mixin(value = PlayerModel.class, priority = 2000)
public class PlayerModelMixin {

    @Inject(
        method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V",
        at = @At("TAIL"),
        require = 0
    )
    private void vmc$monkeBody(AvatarRenderState state, CallbackInfo ci) {
        if (!(state instanceof VmcMonkeRenderState monke)) return;

        PlayerModel self = (PlayerModel) (Object) this;

        // ---- MONKE MODEL transforms (legless body, torso/arm/head tuning) ----
        // Runs whenever this player is flagged monke, INCLUDING your own body while
        // the view is dropped, so the torso rotation etc. actually take effect.
        if (monke.vmc$isMonke()) {
            // CUT OFF THE LEGS (and their second-layer overlays).
            self.leftLeg.visible    = false;
            self.rightLeg.visible   = false;
            self.leftPants.visible  = false;
            self.rightPants.visible = false;
            self.jacket.visible     = false;
            self.leftSleeve.visible = false;
            self.rightSleeve.visible= false;
            self.hat.visible        = false;



            // TORSO: offset (+Y is down in model space), lean, and shorten.
            self.body.y    += (float) MovementConfig.modelTorsoOffsetY;
            self.body.xRot += (float) Math.toRadians(MovementConfig.modelTorsoPitch);
            self.body.yScale *= (float) MovementConfig.modelTorsoScaleY;
            // 1.21.9 removed ModelPart.copyFrom; loadPose(storePose()) copies the full
            // pose (translation + rotation + scale, PartPose now carries scale).
            self.jacket.loadPose(self.body.storePose());

            // ARMS: vertical offset + rotation (added on top of the swing animation).
            float armPitch = (float) Math.toRadians(MovementConfig.modelArmsPitch);
            self.leftArm.y    += (float) MovementConfig.modelArmsOffsetY;
            self.rightArm.y   += (float) MovementConfig.modelArmsOffsetY;
            self.leftArm.xRot  += armPitch;
            self.rightArm.xRot += armPitch;
            self.leftSleeve.loadPose(self.leftArm.storePose());
            self.rightSleeve.loadPose(self.rightArm.storePose());

            // HEAD: vertical offset + rotation; hat layer follows.
            self.head.y    += (float) MovementConfig.modelHeadOffsetY;
            self.head.xRot += (float) Math.toRadians(MovementConfig.modelHeadPitch);
            self.hat.loadPose(self.head.storePose());
        }

        // NOTE: no head-hide here. With the collision-only hitbox shrink (eye height
        // + attachments preserved, see PlayerHitboxMixin) the head model stays at
        // full height, above the dropped VR view — so it's never in the way and your
        // skin's head renders normally, exactly like the dev-20 build.
    }

    // Worn leggings/boots are hidden separately by ArmorLayerMonkeMixin, because
    // armor is drawn by HumanoidArmorLayer on its own model — hiding the bare-leg
    // parts above doesn't touch it.
}
