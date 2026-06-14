package laggyboi.vivemonkecraft.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import java.util.List;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

// =====================================================================
// MOD MENU CONFIG SCREEN
// =====================================================================
//
// This makes a settings screen show up in the "Mods" list (Mod Menu): find
// ViveMonkeCraft, click the little gear/config button, and you get sliders and
// toggles for everything — no need to edit the .properties file by hand.
//
// It's OPTIONAL: it only does anything if Mod Menu + Cloth Config are installed
// (they're listed under "suggests" in fabric.mod.json). The .properties file
// still works exactly the same either way.
//
// HOW IT WORKS:
//   * Each row reads the current MovementConfig value and, when you hit Save,
//     writes your new value back into MovementConfig AND saves the file.
//   * Because the handler reads MovementConfig live, changes apply immediately.
// =====================================================================

public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        // The config screen is built with Cloth Config. If it's NOT installed, return
        // a factory that makes no screen — that way the game never crashes when you
        // click the config button; you just won't get a screen (use the file / keybind
        // / /vmc instead). Install Cloth Config to enable this screen.
        if (!FabricLoader.getInstance().isModLoaded("cloth-config")
            && !FabricLoader.getInstance().isModLoaded("cloth-config2")) {
            return parent -> null;
        }
        // "parent" is the Mods screen we came from; we return to it on Save/Cancel.
        return parent -> buildScreen(parent);
    }

    private Screen buildScreen(Screen parent) {
        // Sync fields from the file first, so the sliders show the latest values.
        MovementConfig.load();

        // PRESET FIX: Cloth Config calls every entry's save consumer IN REGISTRATION
        // ORDER before it calls setSavingRunnable. Because the preset dropdown is
        // registered first, its save consumer would run first — but then EVERY
        // slider's save consumer fires next and overwrites the preset values with the
        // stale on-screen values.
        //
        // Fix: the dropdown only stores the chosen name here. The actual applyPreset()
        // call is deferred to setSavingRunnable, which runs AFTER all slider consumers
        // have already written their values — so the preset wins.
        final String[] pendingPreset = {"— None —"};

        ConfigBuilder builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Component.literal("ViveMonkeCraft — Gorilla Locomotion"));

        // Runs last when you press "Save" (after all entry save consumers).
        // Apply the preset HERE so it overwrites whatever the sliders wrote.
        builder.setSavingRunnable(() -> {
            if (!pendingPreset[0].equals("— None —")) applyPreset(pendingPreset[0]);
            MovementConfig.save();
        });

        ConfigEntryBuilder eb = builder.entryBuilder();

        // ====================================================================
        // PRESETS — choose a preset from the dropdown and hit Save to apply.
        // After saving, close and re-open the config screen to see the updated
        // slider values (Cloth Config reads field values at screen-open time).
        // ====================================================================
        ConfigCategory presets = builder.getOrCreateCategory(Component.literal("Presets"));

        presets.addEntry(eb.startTextDescription(
            Component.literal("§7Pick a preset and press §fSave§7. Close and re-open this screen to see the updated values you can scroll this menu while presets are open look right there is a height slider."))
            .build());

        presets.addEntry(
            eb.startStringDropdownMenu(Component.literal("Apply Preset"), "— None —",
                    s -> Component.literal(s))
                .setSelections(List.of(
                    "— None —",
                    "tutorial",
                    "Default",
                    "Long Arms",
                    "Zero Gravity",
                    "Speed Run"
                ))
                .setDefaultValue("— None —")
                .setSuggestionMode(false)
                .setTooltip(
                    Component.literal("tutorial    — Use this if you find it hard to move with default."),
                    Component.literal("Default     — Reset all settings to factory defaults."),
                    Component.literal("Gorilla Tag — Authentic GT feel: stronger throws, less air drag."),
                    Component.literal("Zero Gravity— Float after letting go; space-like movement."),
                    Component.literal("Speed Run   — Very powerful launches, long arms, low air drag.")
                )
                // Just STORE the name — applyPreset() is called in setSavingRunnable
                // (which runs after ALL slider save consumers) so the preset wins.
                .setSaveConsumer(p -> pendingPreset[0] = p)
                .build()
        );


        // ====================================================================
        // MOVEMENT
        // ====================================================================
        ConfigCategory movement = builder.getOrCreateCategory(Component.literal("Movement"));

        movement.addEntry(eb.startBooleanToggle(Component.literal("Step assist teleports"), MovementConfig.stepTeleport)
            .setDefaultValue(true)
            .setTooltip(
                Component.literal("ON = step assist places you directly on top of the ledge (instant)."),
                Component.literal("OFF = old behaviour: an upward velocity boost arcs you over it."))
            .setSaveConsumer(v -> MovementConfig.stepTeleport = v).build());

        movement.addEntry(eb.startDoubleField(Component.literal("Push speed"), MovementConfig.pullStrength)
            .setDefaultValue(2.0).setMin(0.0).setMax(8.0)
            .setTooltip(Component.literal("How strongly your swing moves you. 1.0 = 1:1, higher = faster. (Ignored in anchor mode — that is always 1:1.)"))
            .setSaveConsumer(v -> MovementConfig.pullStrength = v).build());

        movement.addEntry(eb.startDoubleField(Component.literal("Ground friction (anti-slide)"), MovementConfig.groundFriction)
            .setDefaultValue(0.5).setMin(0.0).setMax(1.0)
            .setTooltip(Component.literal("1.0 = slide forever, 0.5 = stops fast, 0.2 = near-instant."))
            .setSaveConsumer(v -> MovementConfig.groundFriction = v).build());

        movement.addEntry(eb.startDoubleField(Component.literal("Air friction"), MovementConfig.airFriction)
            .setDefaultValue(1.0).setMin(0.0).setMax(1.0)
            .setTooltip(
                Component.literal("How much horizontal speed bleeds off while airborne and NOT gripping."),
                Component.literal("1.0 = normal Minecraft drag  (velocity × 0.91 each tick)"),
                Component.literal("0.5 = half drag  (carries momentum further after a throw)"),
                Component.literal("0.0 = no drag  (throws carry forever — useful in Zero-G)")
            )
            .setSaveConsumer(v -> MovementConfig.airFriction = v).build());

        movement.addEntry(eb.startDoubleField(Component.literal("Wall stickiness"), MovementConfig.wallStickiness)
            .setDefaultValue(1.0).setMin(0.0).setMax(1.0)
            .setTooltip(Component.literal("How hard hands cling to walls while gripping. 1.0 = hang forever, 0.5 = slowly slide down, 0.0 = no clinging (gravity pulls you off)."))
            .setSaveConsumer(v -> MovementConfig.wallStickiness = v).build());

        movement.addEntry(eb.startDoubleField(Component.literal("Floor stickiness"), MovementConfig.floorStickiness)
            .setDefaultValue(1.0).setMin(0.0).setMax(1.0)
            .setTooltip(Component.literal("How much you slide across the floor while a hand is planted. 1.0 = stop dead (sticks to spot), 0.5 = some glide, 0.0 = ice-like (slides forever)."))
            .setSaveConsumer(v -> MovementConfig.floorStickiness = v).build());

        movement.addEntry(eb.startDoubleField(Component.literal("Jitter filter (minImpulse)"), MovementConfig.minImpulse)
            .setDefaultValue(0.002).setMin(0.0).setMax(0.1)
            .setTooltip(Component.literal("Movements smaller than this are ignored so a resting hand stays still."))
            .setSaveConsumer(v -> MovementConfig.minImpulse = v).build());

        // ====================================================================
        // REACH & BODY
        // ====================================================================
        ConfigCategory body = builder.getOrCreateCategory(Component.literal("Reach & Body"));

        body.addEntry(eb.startDoubleField(Component.literal("Gorilla arm length"), MovementConfig.handReachMultiplier)
            .setDefaultValue(2.5).setMin(1.0).setMax(6.0)
            .setTooltip(Component.literal("Longer arms = reach the ground with less real-arm reach. 2.5 default, raise to 3.0+"))
            .setSaveConsumer(v -> MovementConfig.handReachMultiplier = v).build());

        body.addEntry(eb.startDoubleField(Component.literal("Max arm length (blocks)"), MovementConfig.maxArmLength)
            .setDefaultValue(3.0).setMin(1.5).setMax(8.0)
            .setTooltip(Component.literal("Hard limit on how far a hand can be from your head."))
            .setSaveConsumer(v -> MovementConfig.maxArmLength = v).build());

        body.addEntry(eb.startDoubleField(Component.literal("Hand grab size"), MovementConfig.handRadius)
            .setDefaultValue(0.12).setMin(0.02).setMax(0.5)
            .setTooltip(Component.literal("Radius of the hand touch sphere. Bigger = easier to grab."))
            .setSaveConsumer(v -> MovementConfig.handRadius = v).build());

        body.addEntry(eb.startDoubleField(Component.literal("Hitbox height (legs)"), MovementConfig.hitboxHeightScale)
            .setDefaultValue(0.25).setMin(0.2).setMax(1.0)
            .setTooltip(Component.literal("Shrinks your collision box height to climb onto blocks. 1.0 = normal, 0.25 = compact (default)."))
            .setSaveConsumer(v -> MovementConfig.hitboxHeightScale = v).build());

        body.addEntry(eb.startBooleanToggle(Component.literal("Step assist"), MovementConfig.stepAssist)
            .setDefaultValue(true)
            .setTooltip(Component.literal("Raise step height so legs stop snagging on block edges."))
            .setSaveConsumer(v -> MovementConfig.stepAssist = v).build());

        body.addEntry(eb.startDoubleField(Component.literal("Step height"), MovementConfig.stepHeight)
            .setDefaultValue(1.5).setMin(0.5).setMax(2.0)
            .setTooltip(Component.literal("Ledge height you can step over. 0.6 = vanilla, 1.5 = 1.5 blocks (default)."))
            .setSaveConsumer(v -> MovementConfig.stepHeight = v).build());

        // ====================================================================
        // JUMP & GRAVITY
        // ====================================================================
        ConfigCategory jump = builder.getOrCreateCategory(Component.literal("Jump & Gravity"));

        jump.addEntry(eb.startDoubleField(Component.literal("Jump threshold"), MovementConfig.velocityLimit)
            .setDefaultValue(0.06).setMin(0.0).setMax(1.0)
            .setTooltip(Component.literal("You only launch on release if you were moving faster than this."))
            .setSaveConsumer(v -> MovementConfig.velocityLimit = v).build());

        jump.addEntry(eb.startDoubleField(Component.literal("Jump power"), MovementConfig.jumpMultiplier)
            .setDefaultValue(1.4).setMin(0.0).setMax(5.0)
            .setTooltip(Component.literal("How much your speed is multiplied into the launch. 1.0 = release speed, 1.4 = boost."))
            .setSaveConsumer(v -> MovementConfig.jumpMultiplier = v).build());

        jump.addEntry(eb.startDoubleField(Component.literal("Max launch speed"), MovementConfig.maxJumpSpeed)
            .setDefaultValue(1.0).setMin(0.1).setMax(5.0)
            .setTooltip(Component.literal("Hard cap on launch speed (blocks/tick). 1.0 = 20 blocks/sec."))
            .setSaveConsumer(v -> MovementConfig.maxJumpSpeed = v).build());

        jump.addEntry(eb.startDoubleField(Component.literal("Jump smoothing (ticks)"), MovementConfig.velocityHistorySize)
            .setDefaultValue(6.0).setMin(1.0).setMax(20.0)
            .setTooltip(Component.literal("How many ticks of movement are averaged for the launch."))
            .setSaveConsumer(v -> MovementConfig.velocityHistorySize = v).build());

        jump.addEntry(eb.startDoubleField(Component.literal("Gravity"), MovementConfig.gravityMultiplier)
            .setDefaultValue(1.0).setMin(0.0).setMax(1.0)
            .setTooltip(
                Component.literal("Scales gravity while airborne and NOT gripping."),
                Component.literal("1.0 = normal Minecraft gravity  (default)"),
                Component.literal("0.5 = half gravity  (slow fall, floaty)"),
                Component.literal("0.0 = zero gravity  (float in place after releasing)"),
                Component.literal("Can also be changed in-game with /vmc gravity <0-1> (op required).")
            )
            .setSaveConsumer(v -> MovementConfig.gravityMultiplier = v).build());

        // ====================================================================
        // VISUAL
        // ====================================================================
        ConfigCategory visual = builder.getOrCreateCategory(Component.literal("Visual"));

        visual.addEntry(eb.startBooleanToggle(Component.literal("Show hand markers"), MovementConfig.showHandMarkers)
            .setDefaultValue(true)
            .setTooltip(Component.literal("Render split arm lines at your hands. Green = touching a block, red = not."))
            .setSaveConsumer(v -> MovementConfig.showHandMarkers = v).build());

        visual.addEntry(eb.startBooleanToggle(Component.literal("Clamp hand models to surfaces"), MovementConfig.clampHandModels)
            .setDefaultValue(true)
            .setTooltip(
                Component.literal("While gripping, the Vivecraft hand model is drawn ON the block face"),
                Component.literal("instead of sinking inside it (like Gorilla Tag's hand followers)."),
                Component.literal("Purely visual — physics always uses your real hand."))
            .setSaveConsumer(v -> MovementConfig.clampHandModels = v).build());

        visual.addEntry(eb.startBooleanToggle(Component.literal("Real Monke (gorilla size)"), MovementConfig.realMonke)
            .setDefaultValue(false)
            .setTooltip(
                Component.literal("Caps your collision box HEIGHT at 0.5 blocks on both client and"),
                Component.literal("server so you fit through 1-block tunnels. Height only — width,"),
                Component.literal("model, camera scale and reach stay normal. Does NOT drop the view."))
            .setSaveConsumer(v -> MovementConfig.realMonke = v).build());

        visual.addEntry(eb.startDoubleField(Component.literal("Camera height offset"), MovementConfig.cameraHeightOffset)
            .setDefaultValue(0.0).setMin(0.0).setMax(1.5)
            .setTooltip(
                Component.literal("Blocks to LOWER the VR view + hands. 0 = off."),
                Component.literal("Trade-off: makes your OWN body model look squashed toward the"),
                Component.literal("floor (it stays anchored to your feet). Real Monke does not use it."))
            .setSaveConsumer(v -> MovementConfig.cameraHeightOffset = v).build());

        visual.addEntry(eb.startBooleanToggle(Component.literal("Monke model (no legs)"), MovementConfig.monkeModel)
            .setDefaultValue(false)
            .setTooltip(
                Component.literal("The Gorilla Tag body: legs removed, torso shortened."),
                Component.literal("Synced via monke-server — every player with the mod sees every"),
                Component.literal("other opted-in player legless. Tune the body parts below."))
            .setSaveConsumer(v -> MovementConfig.monkeModel = v).build());

        visual.addEntry(eb.startDoubleField(Component.literal("Monke torso offset Y"), MovementConfig.modelTorsoOffsetY)
            .setDefaultValue(0.0).setMin(-12.0).setMax(12.0)
            .setTooltip(Component.literal("Torso up/down in model pixels (1 px = 1/16 block, +down)."))
            .setSaveConsumer(v -> MovementConfig.modelTorsoOffsetY = v).build());

        visual.addEntry(eb.startDoubleField(Component.literal("Monke torso pitch (deg)"), MovementConfig.modelTorsoPitch)
            .setDefaultValue(-120.0).setMin(-360.0).setMax(360.0)
            .setTooltip(Component.literal("Torso rotation in degrees — full -360..360 allowed. -120 = GT lean (default)."))
            .setSaveConsumer(v -> MovementConfig.modelTorsoPitch = v).build());

        visual.addEntry(eb.startDoubleField(Component.literal("Monke torso height scale"), MovementConfig.modelTorsoScaleY)
            .setDefaultValue(0.75).setMin(0.1).setMax(2.0)
            .setTooltip(Component.literal("Torso height multiplier. 0.75 = a bit shorter than the arms."))
            .setSaveConsumer(v -> MovementConfig.modelTorsoScaleY = v).build());

        visual.addEntry(eb.startDoubleField(Component.literal("Monke arms offset Y"), MovementConfig.modelArmsOffsetY)
            .setDefaultValue(0.0).setMin(-12.0).setMax(12.0)
            .setTooltip(Component.literal("Arms up/down in model pixels (+down)."))
            .setSaveConsumer(v -> MovementConfig.modelArmsOffsetY = v).build());

        visual.addEntry(eb.startDoubleField(Component.literal("Monke arms pitch (deg)"), MovementConfig.modelArmsPitch)
            .setDefaultValue(0.0).setMin(-360.0).setMax(360.0)
            .setTooltip(Component.literal("Extra arm rotation in degrees (added on top of the swing animation)."))
            .setSaveConsumer(v -> MovementConfig.modelArmsPitch = v).build());

        visual.addEntry(eb.startDoubleField(Component.literal("Monke head offset Y"), MovementConfig.modelHeadOffsetY)
            .setDefaultValue(0.0).setMin(-12.0).setMax(12.0)
            .setTooltip(Component.literal("Head up/down in model pixels (+down)."))
            .setSaveConsumer(v -> MovementConfig.modelHeadOffsetY = v).build());

        visual.addEntry(eb.startDoubleField(Component.literal("Monke head pitch (deg)"), MovementConfig.modelHeadPitch)
            .setDefaultValue(0.0).setMin(-360.0).setMax(360.0)
            .setTooltip(Component.literal("Extra head rotation in degrees (added on top of look direction)."))
            .setSaveConsumer(v -> MovementConfig.modelHeadPitch = v).build());


        // ====================================================================
        // GT PHYSICS — everything about the anchor-mode port of the official
        // GorillaLocomotion algorithm lives on its own page.
        // ====================================================================
        ConfigCategory gtPage = builder.getOrCreateCategory(Component.literal("GT Physics"));

        gtPage.addEntry(eb.startBooleanToggle(Component.literal("GT physics beta (anchor mode)"), MovementConfig.gtPhysics)
                .setDefaultValue(true)
                .setTooltip(
                        Component.literal("ON = official GorillaLocomotion algorithm: hands ANCHOR to the spot"),
                        Component.literal("they touch and your body is dragged 1:1 (Push speed + stickiness ignored)."),
                        Component.literal("OFF = older speed-based model (swing speed × Push speed)."),
                        Component.literal("its quite bad if you ask me"))
                .setSaveConsumer(v -> MovementConfig.gtPhysics = v).build());

        gtPage.addEntry(eb.startDoubleField(Component.literal("Push strength"), MovementConfig.gtPushStrength)
                .setDefaultValue(1.0).setMin(0.1).setMax(5.0)
                .setTooltip(
                        Component.literal("Body movement = hand movement × this."),
                        Component.literal("1.0 = authentic Gorilla Tag 1:1, 2.0 = twice as far, 0.5 = half."))
                .setSaveConsumer(v -> MovementConfig.gtPushStrength = v).build());

        gtPage.addEntry(eb.startDoubleField(Component.literal("Drag gain"), MovementConfig.gtDragGain)
                .setDefaultValue(0.30).setMin(0.05).setMax(0.95)
                .setTooltip(
                        Component.literal("Fraction of the distance to the anchor corrected each tick."),
                        Component.literal("0.30 = smooth (default), 0.6 = snappier, 0.2 = softer."),
                        Component.literal("Keep below 1.0 or grabs overshoot and bounce."))
                .setSaveConsumer(v -> MovementConfig.gtDragGain = v).build());

        gtPage.addEntry(eb.startDoubleField(Component.literal("Unstick distance (blocks)"), MovementConfig.gtUnstickDistance)
                .setDefaultValue(1.0).setMin(0.2).setMax(3.0)
                .setTooltip(
                        Component.literal("How far a hand may stray from its anchor before the grip releases."),
                        Component.literal("Official Gorilla Tag uses 1.0."))
                .setSaveConsumer(v -> MovementConfig.gtUnstickDistance = v).build());

        gtPage.addEntry(eb.startDoubleField(Component.literal("Ice slip"), MovementConfig.gtIceSlip)
                .setDefaultValue(0.95).setMin(0.0).setMax(1.0)
                .setTooltip(
                        Component.literal("How fast an anchor drifts toward the hand on ice."),
                        Component.literal("0 = ice grips like stone, 0.95 = push off only (default)."))
                .setSaveConsumer(v -> MovementConfig.gtIceSlip = v).build());

        //Experimental page For Experimental stuff
        ConfigCategory Experimental = builder.getOrCreateCategory(Component.literal("Experimental"));

        Experimental.addEntry(eb.startBooleanToggle(Component.literal("Ice floor = ice wall (experimental)"), MovementConfig.iceFloorWallLogic)
                .setDefaultValue(false)
                .setTooltip(
                        Component.literal("Treat grabbing an ICE FLOOR exactly like an ice WALL: gravity on,"),
                        Component.literal("no anchor glue, pure push-off momentum. Legacy physics only."),
                        Component.literal("Experimental — for testing the feel."),
                        Component.literal("leave comment which one is better this or default"))
                .setSaveConsumer(v -> MovementConfig.iceFloorWallLogic = v).build());

        Experimental.addEntry(eb.startBooleanToggle(Component.literal("Punch mining (experimental)"), MovementConfig.punchMining)
                .setDefaultValue(false)
                .setTooltip(
                        Component.literal("Break the block your hand touches — but only while holding the"),
                        Component.literal("tool MEANT for it (pickaxe on stone, etc.) AND only when you PUNCH it"),
                        Component.literal("(hand speed into the block). Gentle grabs never mine, so climbing"),
                        Component.literal("with the tool in hand stays safe. Off by default."))
                .setSaveConsumer(v -> MovementConfig.punchMining = v).build());

        Experimental.addEntry(eb.startDoubleField(Component.literal("Block_Breaking Threshold"),
                        MovementConfig.punchMiningThreshold)
                            .setDefaultValue(0.08).setMin(0.0).setMax(1)
                            .setTooltip(
                             Component.literal("how easily you break blocks"),
                             Component.literal("0 make block damage just by touching"))
                             .setSaveConsumer(V -> MovementConfig.punchMiningThreshold = V).build());

        Experimental.addEntry(eb.startBooleanToggle(Component.literal("Punch mining: no tool needed"), MovementConfig.punchMiningNoTool)
                .setDefaultValue(false)
                .setTooltip(
                        Component.literal("Let punch mining break blocks with ANY item (even bare hands) —"),
                        Component.literal("hand speed alone decides. Tool-required blocks still won't drop"),
                        Component.literal("items without the right tool, like vanilla. Off by default."))
                .setSaveConsumer(v -> MovementConfig.punchMiningNoTool = v).build());

        Experimental.addEntry(eb.startBooleanToggle(Component.literal("Magma block sides hurt"), MovementConfig.magmaTouchDamage)
                .setDefaultValue(true)
                .setTooltip(
                        Component.literal("Grabbing a magma block on ANY face (not just standing on top)"),
                        Component.literal("deals the same hot-floor damage. Fire resistance negates it."))
                .setSaveConsumer(v -> MovementConfig.magmaTouchDamage = v).build());


        return builder.build();
    }

    // =========================================================================
    // PRESETS
    // =========================================================================

    // Each preset sets the subset of settings that define its feel.
    // The overall builder.setSavingRunnable(MovementConfig::save) persists
    // everything to disk when the player presses Save.
    private void applyPreset(String preset) {
        switch (preset) {
            case "tutorial":
                //it gives every helping settings turned on
                MovementConfig.pullStrength        = 2.5;
                MovementConfig.groundFriction      = 1.0;
                MovementConfig.airFriction         = 0.1;
                MovementConfig.wallStickiness      = 1.0;
                MovementConfig.floorStickiness     = 1.0;
                MovementConfig.minImpulse          = 0.002;
                MovementConfig.handReachMultiplier = 1.0;
                MovementConfig.velocityLimit       = 0.05;
                MovementConfig.jumpMultiplier      = 1.8;
                MovementConfig.maxArmLength        = 3.0;
                MovementConfig.handRadius          = 0.12;
                MovementConfig.hitboxHeightScale   = 0.25;
                MovementConfig.stepAssist          = true;
                MovementConfig.stepTeleport        = true;
                MovementConfig.stepHeight          = 1.5;
                MovementConfig.jumpMultiplier      = 1.5;
                MovementConfig.maxJumpSpeed        = 1.0;
                MovementConfig.velocityHistorySize = 6;
                MovementConfig.gravityMultiplier   = 1.0;
                MovementConfig.showHandMarkers     = true;
                MovementConfig.monkeModel          = true;
                MovementConfig.realMonke           = true;
                MovementConfig.modelTorsoPitch     = -120.0;
                MovementConfig.gtPhysics           = false;
                break;

            case "Default":
                // Delegate to the single source of truth (also used on first run /
                // version reset, and dependency-free so it works without Cloth).
                MovementConfig.applyDefaultPreset();
                break;

            case "Long Arms":
                // Closer to the official Gorilla Tag experience:
                //   - Stronger pull so arm swings feel punchy.
                //   - Lower air drag so throws carry farther.
                //   - Slightly easier throw trigger (lower velocityLimit).
                //   - More powerful launches with a higher cap.
                //   - Walls SLIDE (no clinging) so you keep momentum instead of sticking.
                //   - MONKE MODEL on: the legless GT body with the -120° torso lean.
                MovementConfig.pullStrength        = 2.5;
                MovementConfig.groundFriction      = 0.4;
                MovementConfig.airFriction         = 0.1;
                MovementConfig.wallStickiness      = 0.9;
                MovementConfig.floorStickiness     = 1.0;
                MovementConfig.minImpulse          = 0.002;
                MovementConfig.handReachMultiplier = 3.5;
                MovementConfig.velocityLimit       = 0.05;
                MovementConfig.jumpMultiplier      = 1.8;
                MovementConfig.maxJumpSpeed        = 1.5;
                MovementConfig.velocityHistorySize = 5;
                MovementConfig.gravityMultiplier   = 1.0;
                MovementConfig.showHandMarkers     = true;
                MovementConfig.monkeModel          = true;
                MovementConfig.realMonke           = true;
                MovementConfig.modelTorsoPitch     = -120.0;

                break;

            case "Zero Gravity":
                // Space/floating experience:
                //   - No gravity: you float after releasing a surface.
                //   - Very low air drag: momentum carries indefinitely.
                //   - Bigger throws so you can actually navigate.
                //   - Full wall stickiness so you can push off any surface.
                MovementConfig.gravityMultiplier   = 0.1;
                MovementConfig.airFriction         = 0.05;
                MovementConfig.wallStickiness      = 1.0;
                MovementConfig.floorStickiness     = 0.8;
                MovementConfig.pullStrength        = 2.0;
                MovementConfig.jumpMultiplier      = 2.0;
                MovementConfig.maxJumpSpeed        = 2.5;
                MovementConfig.groundFriction      = 0.9;
                MovementConfig.monkeModel          = true;
                MovementConfig.realMonke           = true;
                MovementConfig.modelTorsoPitch     = -120.0;
                break;

            case "Parkour / Speed Run":
                // Built for covering ground as fast as possible:
                //   - Very high pull strength for huge launches.
                //   - Long gorilla arms to grab from further away.
                //   - Low air drag so speed carries between grabs.
                //   - Fewer history ticks for snappier throw detection.
                //   - High max speed so you can actually rocket.
                MovementConfig.pullStrength        = 5.0;
                MovementConfig.handReachMultiplier = 3.0;
                MovementConfig.maxArmLength        = 4.0;
                MovementConfig.airFriction         = 0.0;
                MovementConfig.jumpMultiplier      = 3.5;
                MovementConfig.maxJumpSpeed        = 3.0;
                MovementConfig.velocityHistorySize = 4;
                MovementConfig.groundFriction      = 0.7;
                MovementConfig.gravityMultiplier   = 1.0;
                MovementConfig.monkeModel          = true;
                MovementConfig.realMonke           = true;
                MovementConfig.modelTorsoPitch     = -120.0;
                break;

            default:
                // "— None —" or anything unrecognised: do nothing.
                break;
        }
    }
}
