package laggyboi.vivemonkecraft.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import laggyboi.vivemonkecraft.client.VmcMonkeRenderState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// =====================================================================
// MONKE MODEL — hide LEG + BOOT armor
// =====================================================================
//
// The monke look already hides the player's bare legs (PlayerModelMixin), but
// worn leggings/boots are drawn by a SEPARATE layer (HumanoidArmorLayer) on its
// own armor model, so they'd still float where the legs used to be. Skip just
// those two slots for monke players; helmet and chestplate render normally.
//
// HumanoidArmorLayer.render(...) carries the render state (which knows whether
// this player is monke) but its per-slot renderArmorPiece(...) does not — so we
// latch the flag in render() and read it back in renderArmorPiece(). Armor
// rendering is single-threaded and one render() fully drives its own four
// renderArmorPiece() calls, so a plain instance field is safe (no re-entrancy).
// =====================================================================
@Mixin(HumanoidArmorLayer.class)
public class ArmorLayerMonkeMixin {

    @Unique
    private boolean vmc$hideLegArmor = false;

    // render() has the full render state — record whether this player is monke.
    // Explicit descriptor so we hit the HumanoidRenderState overload, not the
    // EntityRenderState bridge method.
    @Inject(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V",
            at = @At("HEAD")
    )
    private void vmc$captureMonke(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                  HumanoidRenderState state, float yRot, float xRot, CallbackInfo ci) {
        vmc$hideLegArmor = state instanceof VmcMonkeRenderState monke && monke.vmc$isMonke();
    }

    // Per-slot piece — cancel only LEGS (leggings) and FEET (boots) for monke players.
    @Inject(
            method = "renderArmorPiece",
            at = @At("HEAD"),
            cancellable = true
    )
    private void vmc$skipLegArmor(PoseStack poseStack, MultiBufferSource buffer, ItemStack stack,
                                  EquipmentSlot slot, int packedLight, HumanoidModel<?> model,
                                  CallbackInfo ci) {
        if (vmc$hideLegArmor && (slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET)) {
            ci.cancel();
        }
    }
}
