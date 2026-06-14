package laggyboi.vivemonkecraft.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

// This is the CLIENT entry point — Minecraft calls onInitializeClient() once when
// the mod loads. We set up: the toggle keybind, a /vmc chat command, and the tick.

public class VivemonkecraftClient implements ClientModInitializer {

    // Is gorilla locomotion currently on? Static so the mixins can read it.
    // Starts OFF — auto-enabled once when the player joins a world (see onEndTick).
    private static boolean enabled = false;

    // Whether we have already auto-enabled this world session.
    // Reset to false on disconnect so we auto-start again next time a world loads.
    private boolean autoStarted = false;

    // VR-active state last tick — used to edge-trigger auto enable/disable so the mod
    // turns on when a headset appears and off when it goes away.
    private boolean vrWasActive = false;

    // Ticks to wait after joining before auto-enabling the mod.
    // This gives the server's config packet time to arrive so we know whether the
    // server runs the companion mod (monke-server) BEFORE we decide.
    // 10 ticks = 500 ms — plenty of time even over a WAN connection.
    private static final int PACKET_WAIT_TICKS = 10;

    // Counts ticks since the last JOIN event.  Incremented each tick until
    // autoStarted becomes true, then ignored.  Reset to 0 on JOIN and DISCONNECT.
    private int ticksSinceJoin = 0;

    // Whether the "this server doesn't run the ViveMonke server mod" notice has
    // already been shown this connection (shown at most once per join).
    private boolean warnedNoServerMod = false;

    // Last applied "Real Monke" state — re-applies the collision box / server
    // shrink when the setting (or the mod's enabled state) flips mid-game.
    private boolean lastRealMonke = false;

    // Last announced "monke model" (legless look) state.
    private boolean lastMonkeModel = false;

    // Whether we last told a DEDICATED server we're gripping (for the no-fall-damage
    // slide). Tracks state so we send one "released" packet when gripping ends rather
    // than spamming it. Singleplayer/LAN host doesn't use this (handled in the handler).
    private boolean wallSlideSent = false;

    // Whether a GUI screen was open last tick — used to drop grips exactly once
    // when a screen opens (inventory/chat/settings must not move the player).
    private boolean wasInGui = false;

    // The dimension the player was in last tick. Changing dimension (e.g. to the
    // Nether) rebuilds the player entity, which recomputes its collision box ONCE
    // and then caches it — so the Real Monke shrink silently reverts to full size
    // until something forces a refresh (the in-game "toggle off+on" fix). We watch
    // for the change and re-apply automatically.
    private net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> lastDimension = null;

    // The player ENTITY instance last tick. Respawn (and dimension change) replace it
    // with a new LocalPlayer; when that happens we re-apply Real Monke / monke model,
    // otherwise the rebuilt entity reverts to full 2-block height after dying.
    private net.minecraft.client.player.LocalPlayer lastPlayerRef = null;

    // The physics handler.
    private GorillaLocomotionHandler handler;

    // Toggle keybind — UNBOUND by default (no key assigned out of the box).
    // To toggle via Vivecraft radial menu: go to VR Settings -> Radial Menu and assign
    // the "ViveMonkeCraft: Toggle" keybind to a radial slot.
    // To toggle via keyboard: rebind in Options -> Controls -> Miscellaneous.
    private final KeyMapping toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.vivemonkecraft.toggle",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            KeyMapping.CATEGORY_MISC
    ));

    public void onInitializeClient() {

        handler = new GorillaLocomotionHandler();

        // Register the per-frame hand marker renderer (replaces old particles).
        HandMarkerRenderer.register();

        // Register the camera stabilization vignette (motion-sickness reduction).
        CameraStabilizationRenderer.register();

        // Load the editable config at startup (creates it the first time).
        MovementConfig.load();

        // Register the S2C payload type so Fabric can decode incoming server packets.
        // This must happen during init (before any world joins).
        PayloadTypeRegistry.playS2C().register(
                ServerConfigPayload.ID,
                ServerConfigPayload.STREAM_CODEC
        );

        // Real Monke: C2S request asking monke-server to shrink our hitbox height.
        PayloadTypeRegistry.playC2S().register(
                RealMonkeC2SPayload.ID,
                RealMonkeC2SPayload.STREAM_CODEC
        );

        // Wall slide: C2S keepalive telling monke-server we're gripping, so it zeroes
        // our (server-authoritative) fall distance — the dedicated-server half of the
        // no-fall-damage slide. Singleplayer/LAN host handles this in the handler by
        // resetting the integrated server player directly, so the packet is dedicated-only.
        PayloadTypeRegistry.playC2S().register(
                WallSlideC2SPayload.ID,
                WallSlideC2SPayload.STREAM_CODEC
        );

        // Magma touch: C2S signal telling monke-server to apply hot-floor damage while
        // a hand grips a magma block (server-authoritative, so dedicated-only — singleplayer
        // hurts the integrated server player directly in the handler).
        PayloadTypeRegistry.playC2S().register(
                MagmaTouchC2SPayload.ID,
                MagmaTouchC2SPayload.STREAM_CODEC
        );

        // Monke model sync: we announce our legless look (C2S) and receive
        // everyone else's (S2C broadcast from monke-server).
        PayloadTypeRegistry.playC2S().register(
                MonkeModelC2SPayload.ID,
                MonkeModelC2SPayload.STREAM_CODEC
        );
        PayloadTypeRegistry.playS2C().register(
                MonkeModelS2CPayload.ID,
                MonkeModelS2CPayload.STREAM_CODEC
        );
        ClientPlayNetworking.registerGlobalReceiver(
                MonkeModelS2CPayload.ID,
                (payload, context) -> MonkeModelClientSet.set(payload.player(), payload.enabled())
        );

        // Embedded server logic: when THIS client hosts (singleplayer / Open-to-LAN /
        // Essential), its integrated server plays the role of monke-server — so two
        // players who both have this mod can play together over LAN with NO separate
        // server jar. Harmless on a pure client (these server events never fire when
        // connected to a remote server). Payload types were registered above.
        EmbeddedServerLogic.register();

        // When the server companion mod (monke-server) sends its config, store the
        // limits. Receiving this packet IS the multiplayer authorization: without it
        // the mod refuses to enable on a dedicated server (see serverAuthorized).
        ClientPlayNetworking.registerGlobalReceiver(
                ServerConfigPayload.ID,
                (payload, context) -> {
                    ServerLimits.apply(payload);

                    context.client().execute(() -> {
                        if (!payload.modEnabled()) {
                            // Server banned the mod — force-disable immediately, even
                            // if auto-start already fired before the packet arrived.
                            if (enabled) {
                                enabled = false;
                                handler.onDisable(context.client());
                            }
                            if (context.client().player != null) {
                                context.client().gui.setOverlayMessage(
                                    Component.literal("§e[ViveMonkeCraft] §cDisabled by server"),
                                    false
                                );
                            }
                        } else if (autoStarted && !enabled) {
                            // Authorization arrived AFTER the auto-start window closed
                            // (slow connection) — turn the mod on now.
                            applyEnabled(true);
                        }
                    });
                }
        );

        // Reset state when the player joins a world so the tick handler will
        // fire applyEnabled(true) once the player entity is ready (after the
        // PACKET_WAIT_TICKS grace window).
        ClientPlayConnectionEvents.JOIN.register((networkHandler, sender, client) -> {
            autoStarted       = false;
            ticksSinceJoin    = 0;
            warnedNoServerMod = false;
            lastRealMonke     = false;   // re-apply the shrink after the join settles
            lastMonkeModel    = false;   // re-announce the legless look too
        });

        // On disconnect: turn off gorilla locomotion, clear server limits, and reset
        // counters so the next world join auto-starts again after the grace window.
        ClientPlayConnectionEvents.DISCONNECT.register((networkHandler, client) -> {
            autoStarted       = false;
            ticksSinceJoin    = 0;
            warnedNoServerMod = false;
            lastRealMonke     = false;
            lastMonkeModel    = false;
            wasInGui          = false;
            lastDimension     = null;
            wallSlideSent     = false;   // next world starts with no pending grip state
            vrWasActive       = false;   // re-evaluate VR presence fresh next world
            MonkeModelClientSet.clear(); // stale legless flags don't carry over
            ServerLimits.reset();   // clear caps + authorization for the next world
            if (enabled) {
                enabled = false;
                handler.onDisable(client);
            }
        });

        // Run our logic at the end of every client tick.
        ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);

        // Register the /vmc chat command (open chat with T, type /vmc).
        //   /vmc               -> toggle on/off
        //   /vmc on|off        -> set explicitly
        //   /vmc reload        -> re-read the config file
        //   /vmc gravity <0-1> -> set the gravity multiplier (operator only)
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> {
            dispatcher.register(literal("vmc")
                .executes(ctx -> { toggle(); return 1; })
                .then(literal("on").executes(ctx -> { applyEnabled(true); return 1; }))
                .then(literal("off").executes(ctx -> { applyEnabled(false); return 1; }))
                .then(literal("reload").executes(ctx -> { reloadConfig(); return 1; }))
                .then(literal("set")
                    .then(argument("setting", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            String typed = builder.getRemainingLowerCase();
                            for (String n : MovementConfig.settingNames()) {
                                if (n.toLowerCase().startsWith(typed)) builder.suggest(n);
                            }
                            return builder.buildFuture();
                        })
                        .then(argument("value", StringArgumentType.word())
                            .executes(ctx -> {
                                Minecraft mc = Minecraft.getInstance();
                                String name  = StringArgumentType.getString(ctx, "setting");
                                String value = StringArgumentType.getString(ctx, "value");
                                String result = MovementConfig.setByName(name, value);
                                if (mc.player != null) {
                                    mc.gui.setOverlayMessage(Component.literal(
                                        result != null
                                            ? "§e[ViveMonkeCraft] §f" + result
                                            : "§c[ViveMonkeCraft] §fUnknown setting or bad value: "
                                                + name + " " + value),
                                        false);
                                }
                                return result != null ? 1 : 0;
                            })
                        )
                    )
                )
                .then(literal("gravity")
                    .then(argument("level", DoubleArgumentType.doubleArg(0.0, 1.0))
                        .executes(ctx -> {
                            Minecraft mc = Minecraft.getInstance();
                            // Op-lock: requires permission level 2 (operator).
                            // In singleplayer the host is always level 4, so this always works.
                            // On a server it reflects what the server reported to the client.
                            if (mc.player == null || !mc.player.hasPermissions(2)) {
                                if (mc.player != null) {
                                    mc.gui.setOverlayMessage(
                                        Component.literal("§c[ViveMonkeCraft] §fNeed operator access to change gravity"),
                                        false
                                    );
                                }
                                return 0;
                            }
                            double level = DoubleArgumentType.getDouble(ctx, "level");
                            MovementConfig.gravityMultiplier = level;
                            MovementConfig.save();
                            mc.gui.setOverlayMessage(
                                Component.literal("§e[ViveMonkeCraft] §fGravity: §b" + level
                                    + (level == 0.0 ? " §7(zero-G)" : level == 1.0 ? " §7(normal)" : "")),
                                false
                            );
                            return 1;
                        })
                    )
                )
            );
        });
    }

    public static boolean isEnabled() {
        return enabled;
    }

    // -----------------------------------------------------------------------
    // Per-tick: keybind handling + physics
    // -----------------------------------------------------------------------

    private void onEndTick(Minecraft client) {
        // Auto-start: wait PACKET_WAIT_TICKS ticks after joining so the server's
        // config packet has time to arrive before we decide to enable.
        // Singleplayer enables unconditionally. On a dedicated server, no packet =
        // no companion mod = the mod STAYS OFF (server-side opt-in, Modrinth policy);
        // the player gets a one-time chat notice explaining why.
        // Done here rather than in JOIN because on QuestCraft the player entity
        // may not be fully constructed yet at the moment JOIN fires.
        if (!autoStarted && client.player != null) {
            ticksSinceJoin++;
            if (ticksSinceJoin >= PACKET_WAIT_TICKS) {
                autoStarted = true;
                // Actually enabling is handled by the VR watcher below (it requires a
                // headset). Here we only warn once if the server hasn't opted in.
                if (!serverAuthorized(client) && !warnedNoServerMod) {
                    warnedNoServerMod = true;
                    client.player.displayClientMessage(Component.literal(
                        "§e[ViveMonkeCraft] §cThis server doesn't run the monke-server "
                        + "companion mod, so gorilla locomotion is disabled here. "
                        + "§7(Server admins: install the ViveMonke server mod to allow it.)"),
                        false);
                }
            }
        }

        // VR PRESENCE DRIVES THE MOD: auto-ON when a headset becomes active (and the
        // server allows it), auto-OFF when VR goes away — so it's inert in plain desktop
        // Minecraft and springs to life in VR. Edge-triggered (acts only on the VR
        // on/off transition), so you can still manually toggle it off within a VR
        // session without it snapping back on every tick.
        boolean vrActive = VivecraftBridge.isVrActive();
        if (autoStarted && client.player != null) {
            if (vrActive && !vrWasActive) {
                VmcDebugLog.event("VR", "headset ACTIVE");
                if (!enabled && serverAuthorized(client) && ServerLimits.modEnabled) applyEnabled(true);
            } else if (!vrActive && vrWasActive) {
                VmcDebugLog.event("VR", "headset INACTIVE");
                if (enabled) applyEnabled(false);
            }
            vrWasActive = vrActive;
        }

        // While the mod is on, disable Vivecraft's teleport (a free-locomotion mod
        // doesn't use it, and the teleport button desyncs the room origin and breaks
        // our physics). Restored automatically when the mod turns off. Enforced every
        // tick so it survives Vivecraft re-initialising on respawn/dimension change.
        VivecraftBridge.setTeleportDisabled(enabled);

        // Toggle on each press of the keybind (keyboard or Vivecraft radial menu).
        while (toggleKey.consumeClick()) {
            toggle();
        }

        // Dimension change (Nether/End portal, /execute in, etc.) AND respawn after
        // death both REPLACE the player entity, which recomputes its collision box
        // once and caches it — so our Real Monke shrink silently reverts (the model
        // springs back to full 2-block height). Force the watchers below to re-apply
        // by clearing their "last" state — same effect as toggling the option off+on.
        // A dimension change is caught by the dimension key; a same-dimension respawn
        // is caught by the player INSTANCE changing (a brand-new LocalPlayer object).
        if (client.player != null) {
            var dim = client.player.level().dimension();
            if ((lastDimension != null && !lastDimension.equals(dim))
                    || client.player != lastPlayerRef) {
                lastRealMonke  = false;
                lastMonkeModel = false;
            }
            lastDimension = dim;
            lastPlayerRef = client.player;
        }

        // Apply / remove Real Monke when the setting or the mod's enabled state
        // flips mid-game (config screen save, /vmc set, preset, toggle).
        boolean monke = enabled && MovementConfig.realMonke;
        if (monke != lastRealMonke && client.player != null) {
            lastRealMonke = monke;
            client.player.refreshDimensions();
            applyRealMonkeScale(client, monke);
        }

        // Announce / retract the monke model (legless look): render it locally for
        // ourselves immediately, and tell the server (dedicated monke-server OR our
        // own integrated/LAN server via the embedded logic) so it broadcasts to all
        // other mod users. canSend() is true whenever a receiver exists on the other
        // end — including our own integrated server when hosting LAN.
        boolean model = enabled && MovementConfig.monkeModel;
        if (model != lastMonkeModel && client.player != null) {
            lastMonkeModel = model;
            MonkeModelClientSet.set(client.player.getUUID(), model);
            if (ClientPlayNetworking.canSend(MonkeModelC2SPayload.ID)) {
                ClientPlayNetworking.send(new MonkeModelC2SPayload(model));
            }
        }

        boolean grippingNow = false;
        boolean magmaNow    = false;
        if (enabled && client.player != null && !client.isPaused()
                && ServerLimits.modEnabled && serverAuthorized(client)) {
            // GUI GUARD: while ANY screen is open (inventory, chat, settings, ...)
            // gorilla locomotion must not move the player. Drop all grips the
            // moment a screen opens, and stay fully inert until it closes — the
            // next tick after closing resumes normally.
            if (client.screen != null) {
                if (!wasInGui) {
                    wasInGui = true;
                    handler.onGuiPause(client);
                }
            } else {
                wasInGui = false;
                handler.tick(client);
                grippingNow = handler.isGripping();
                magmaNow    = handler.isTouchingMagma();
            }
        }

        // No-fall-damage slide, dedicated-server half: keep the companion mod's fall
        // suppression alive while we grip. (Singleplayer/LAN host resets the integrated
        // server player inside the handler, so this only fires on a remote server.)
        syncWallSlide(client, grippingNow);

        // Magma touch, dedicated-server half: ask the companion mod to hurt us while a
        // hand grips a magma block. (Singleplayer/LAN hurts the integrated player in the
        // handler.) Sent each touching tick; hurt invulnerability frames throttle it.
        if (magmaNow && !client.hasSingleplayerServer()
                && ClientPlayNetworking.canSend(MagmaTouchC2SPayload.ID)) {
            ClientPlayNetworking.send(MagmaTouchC2SPayload.INSTANCE);
        }
    }

    // Sends one keepalive per gripping tick and a single "released" packet when
    // gripping ends. No-op in singleplayer/LAN host (handled in the handler) and when
    // no receiver exists on the other end (server without the companion mod).
    private void syncWallSlide(Minecraft client, boolean gripping) {
        if (client.hasSingleplayerServer()) return;
        if (!ClientPlayNetworking.canSend(WallSlideC2SPayload.ID)) return;
        if (gripping) {
            if (!wallSlideSent) VmcDebugLog.event("NET", "→ WallSlide(true) [no-fall-damage]");
            ClientPlayNetworking.send(new WallSlideC2SPayload(true));
            wallSlideSent = true;
        } else if (wallSlideSent) {
            VmcDebugLog.event("NET", "→ WallSlide(false)");
            ClientPlayNetworking.send(new WallSlideC2SPayload(false));
            wallSlideSent = false;
        }
    }

    // -----------------------------------------------------------------------
    // Real Monke — server-synced HEIGHT-ONLY hitbox shrink
    // -----------------------------------------------------------------------

    // The SERVER validates movement with its own copy of the player's box, so the
    // shrink must land on both logical sides or tunnels get rubber-banded:
    //   singleplayer → refresh the integrated server's ServerPlayer; the shared
    //                  PlayerHitboxMixin applies the same height cap to it
    //   dedicated    → ask the monke-server companion via RealMonkeC2SPayload
    private static void applyRealMonkeScale(Minecraft client, boolean on) {
        if (client.hasSingleplayerServer()) {
            var server = client.getSingleplayerServer();
            if (server == null || client.player == null) return;
            java.util.UUID id = client.player.getUUID();
            server.execute(() -> {
                var sp = server.getPlayerList().getPlayer(id);
                if (sp != null) sp.refreshDimensions();   // PlayerHitboxMixin caps the height
            });
        } else if (ServerLimits.packetReceived
                && ClientPlayNetworking.canSend(RealMonkeC2SPayload.ID)) {
            ClientPlayNetworking.send(new RealMonkeC2SPayload(on));
        }
    }

    // -----------------------------------------------------------------------
    // Multiplayer authorization (server-side opt-in)
    // -----------------------------------------------------------------------

    // Singleplayer / hosting a LAN world: always allowed — there is no one to gain
    // an unfair advantage over, and the integrated server can't run server mods.
    // Dedicated server: allowed ONLY after the monke-server companion mod has sent
    // its config packet this connection. No packet = the server didn't opt in =
    // the mod refuses to run (required by Modrinth's movement-mod policy, and it
    // shuts out bad actors who would use this on unsuspecting vanilla servers).
    private static boolean serverAuthorized(Minecraft client) {
        return client.hasSingleplayerServer() || ServerLimits.packetReceived;
    }

    // -----------------------------------------------------------------------
    // Shared toggle logic (used by BOTH the keybind and the /vmc command)
    // -----------------------------------------------------------------------

    private void toggle() {
        applyEnabled(!enabled);
    }

    private void applyEnabled(boolean on) {
        Minecraft client = Minecraft.getInstance();

        // Multiplayer opt-in gate: on a dedicated server without the monke-server
        // companion mod, refuse every enable attempt — keybind, /vmc on, auto-start.
        if (on && !serverAuthorized(client)) {
            if (client.player != null) {
                client.gui.setOverlayMessage(
                    Component.literal("§e[ViveMonkeCraft] §cThis server doesn't run the monke-server mod"),
                    false
                );
            }
            return;
        }

        // Server config always wins: if the server has banned the mod, refuse any
        // attempt to enable it — keybind, /vmc on, or auto-start all end up here.
        if (on && !ServerLimits.modEnabled) {
            if (client.player != null) {
                client.gui.setOverlayMessage(
                    Component.literal("§e[ViveMonkeCraft] §cDisabled by server"),
                    false
                );
            }
            return;
        }

        enabled = on;
        VmcDebugLog.event("STATE", "gorilla locomotion " + (on ? "ENABLED" : "DISABLED"));

        if (on) {
            // Turning ON: re-read the config so edits apply, no restart needed.
            MovementConfig.load();
        } else {
            // Turning OFF: drop grips and restore step height / scale / gravity.
            handler.onDisable(client);
        }

        if (client.player != null) {
            // Refresh the collision box so the shorter "gorilla" hitbox applies/reverts now.
            client.player.refreshDimensions();

            String state = on ? "§aON" : "§cOFF";
            // Show in the action bar (overlay message).
            client.gui.setOverlayMessage(
                Component.literal("§e[ViveMonkeCraft] §fGorilla Locomotion: " + state),
                false
            );
        }
    }

    private void reloadConfig() {
        MovementConfig.load();
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.gui.setOverlayMessage(
                Component.literal("§e[ViveMonkeCraft] §fConfig reloaded"),
                false
            );
        }
    }
}
