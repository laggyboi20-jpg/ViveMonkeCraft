package laggyboi.vivemonkecraft.client;

/**
 * Stores everything received from the server companion mod (monke-server) via
 * {@link ServerConfigPayload}:
 *
 *   ENFORCED LIMITS — backed up server-side:
 *     - modEnabled   (enforced via airborne kick + speed correction)
 *     - maxJumpSpeed (enforced via per-tick position cap)
 *
 *   ALLOWANCES — values the server admin GRANTS to every player, replacing the
 *     client-side default caps in {@link SettingCaps} (e.g. allowPushStrength=5
 *     lets every player on that server run push 5 without needing op).
 *     Sentinels: 0.0 = "not granted" for max-type allowances, -1.0 = "not
 *     granted" for min-type ones (because 0.0 is a meaningful minimum, e.g.
 *     gravity 0 = zero-G allowed).
 *
 * {@link #reset()} on disconnect so singleplayer / the next server starts clean.
 */
public final class ServerLimits {

    /** When false the server has disallowed VR locomotion. */
    public static volatile boolean modEnabled    = true;

    /**
     * True once the server companion mod (monke-server) has sent its config packet
     * during the CURRENT connection. This is the multiplayer opt-in signal: on a
     * dedicated server the mod refuses to run until this is true (Modrinth policy —
     * movement mods need a server-side opt-in). Singleplayer never needs it.
     * Cleared on disconnect.
     */
    public static volatile boolean packetReceived = false;

    private static volatile double sMaxJumpSpeed = 0.0;

    // Allowances (see class doc). Max-type: 0 = not granted. Min-type: -1 = not granted.
    private static volatile double sAllowPushStrength   = 0.0;
    private static volatile double sAllowJumpMultiplier = 0.0;
    private static volatile double sAllowMaxJumpSpeed   = 0.0;
    private static volatile double sAllowHandReach      = 0.0;
    private static volatile double sAllowArmLength      = 0.0;
    private static volatile double sAllowStepHeight     = 0.0;
    private static volatile double sAllowHandRadius     = 0.0;
    private static volatile double sAllowGravityMin     = -1.0;
    private static volatile double sAllowAirFrictionMin = -1.0;

    private ServerLimits() {}

    public static void reset() {
        modEnabled     = true;
        packetReceived = false;
        sMaxJumpSpeed  = 0.0;
        sAllowPushStrength   = 0.0;
        sAllowJumpMultiplier = 0.0;
        sAllowMaxJumpSpeed   = 0.0;
        sAllowHandReach      = 0.0;
        sAllowArmLength      = 0.0;
        sAllowStepHeight     = 0.0;
        sAllowHandRadius     = 0.0;
        sAllowGravityMin     = -1.0;
        sAllowAirFrictionMin = -1.0;
    }

    public static void apply(ServerConfigPayload p) {
        modEnabled     = p.modEnabled();
        packetReceived = true;
        sMaxJumpSpeed  = p.maxJumpSpeed();
        sAllowPushStrength   = p.allowPushStrength();
        sAllowJumpMultiplier = p.allowJumpMultiplier();
        sAllowMaxJumpSpeed   = p.allowMaxJumpSpeed();
        sAllowHandReach      = p.allowHandReach();
        sAllowArmLength      = p.allowArmLength();
        sAllowStepHeight     = p.allowStepHeight();
        sAllowHandRadius     = p.allowHandRadius();
        sAllowGravityMin     = p.allowGravityMin();
        sAllowAirFrictionMin = p.allowAirFrictionMin();
    }

    /**
     * Returns the lower of the player's local maxJumpSpeed and the server's HARD
     * enforcement cap (the one backed by position correction + kicks).
     * Returns {@code local} unchanged when the server cap is 0.0 (no limit).
     */
    public static double maxJumpSpeed(double local) {
        return (sMaxJumpSpeed > 0.0) ? Math.min(local, sMaxJumpSpeed) : local;
    }

    // ── Allowance getters (read by SettingCaps) ─────────────────────────────

    public static double allowPushStrength()   { return sAllowPushStrength; }
    public static double allowJumpMultiplier() { return sAllowJumpMultiplier; }
    public static double allowMaxJumpSpeed()   { return sAllowMaxJumpSpeed; }
    public static double allowHandReach()      { return sAllowHandReach; }
    public static double allowArmLength()      { return sAllowArmLength; }
    public static double allowStepHeight()     { return sAllowStepHeight; }
    public static double allowHandRadius()     { return sAllowHandRadius; }
    public static double allowGravityMin()     { return sAllowGravityMin; }
    public static double allowAirFrictionMin() { return sAllowAirFrictionMin; }
}
