package laggyboi.vivemonkecraft.client;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which players THIS client should render with the monke model (no legs,
 * shorter torso). Fed by {@link MonkeModelS2CPayload} broadcasts from
 * monke-server, plus the local player's own toggle (so it also works in
 * singleplayer with just the client jar). Read every frame by the player
 * model mixins; cleared on disconnect.
 */
public final class MonkeModelClientSet {

    private static final Set<UUID> MONKE = ConcurrentHashMap.newKeySet();

    private MonkeModelClientSet() {}

    public static void set(UUID player, boolean on) {
        if (on) MONKE.add(player);
        else    MONKE.remove(player);
    }

    public static boolean isMonke(UUID player) {
        return MONKE.contains(player);
    }

    public static void clear() {
        MONKE.clear();
    }
}
