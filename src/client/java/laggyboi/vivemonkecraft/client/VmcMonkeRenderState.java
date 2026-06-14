package laggyboi.vivemonkecraft.client;

/**
 * Duck interface stitched onto vanilla's PlayerRenderState by
 * PlayerRenderStateMixin. Carries one flag from the renderer (which knows the
 * player's UUID) to the model (which only sees the render state): should this
 * player be drawn with the monke model (no legs, shorter torso)?
 */
public interface VmcMonkeRenderState {

    boolean vmc$isMonke();

    void vmc$setMonke(boolean monke);

    // True when this is the LOCAL player's own first-person body AND the VR view
    // is dropped (Real Monke / camera offset). The body model is anchored to the
    // feet while the view dropped, so it droops to the floor — the model mixin
    // hides it. Only the local own-body is flagged; other players render normally.
    boolean vmc$hideOwnBody();

    void vmc$setHideOwnBody(boolean hide);
}
