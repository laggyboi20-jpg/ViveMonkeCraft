package laggyboi.vivemonkecraft.client;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// =====================================================================
// EMBEDDED SERVER LOGIC — the monke-server companion, baked into the client
// =====================================================================
//
// A LAN host ("Open to LAN" / Essential invite) runs an INTEGRATED server in
// the same JVM as the client. ServerPlayConnectionEvents / ServerPlayNetworking
// work there, so registering them from the client mod gives a LAN host all the
// server-side behaviour WITHOUT anyone installing the separate ServerBuild jar:
//
//   * sends ServerConfigPayload on join → guests get AUTHORIZED (the packet is
//     the multiplayer opt-in; without it a guest's mod stays off). Defaults to
//     modEnabled=true, no caps/allowances — a friends LAN game is unrestricted.
//   * receives Real Monke requests → tracks who's shrunk so PlayerHitboxMixin
//     can shrink guests' collision boxes on the host (so tunnels validate).
//   * receives Monke Model toggles → broadcasts them to everyone, and replays
//     the current set to joiners, so all mod users see each other legless.
//
// IMPORTANT: this only runs where an integrated server exists (singleplayer /
// LAN host). On a DEDICATED server the client mod isn't loaded at all, so these
// events never fire there — dedicated servers still need the ServerBuild, which
// preserves the server-side opt-in (Modrinth policy). The payload TYPES are
// registered once by VivemonkecraftClient; we only add the server handlers here.
// =====================================================================
public final class EmbeddedServerLogic {

    /** Guests (and host) who have Real Monke on — read by PlayerHitboxMixin. */
    public static final Set<UUID> realMonkePlayers = ConcurrentHashMap.newKeySet();

    /** Players who have the legless Monke Model on — for join replay. */
    private static final Set<UUID> monkeModelPlayers = ConcurrentHashMap.newKeySet();

    private EmbeddedServerLogic() {}

    public static void register() {
        // Authorize every joiner + replay the current monke-model set to them.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            // Unrestricted config — a LAN game among friends needs no caps. (Wire
            // format must match ServerConfigPayload: modEnabled, then 10 doubles.)
            ServerPlayNetworking.send(handler.player, new ServerConfigPayload(
                    true,        // modEnabled
                    0.0,         // maxJumpSpeed (no hard cap)
                    0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,   // allowances unset
                    -1.0, -1.0)); // gravity / air-friction minimums unset

            for (UUID u : monkeModelPlayers) {
                ServerPlayNetworking.send(handler.player, new MonkeModelS2CPayload(u, true));
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.player.getUUID();
            realMonkePlayers.remove(id);
            if (monkeModelPlayers.remove(id)) {
                MonkeModelS2CPayload off = new MonkeModelS2CPayload(id, false);
                for (ServerPlayer p : PlayerLookup.all(server)) {
                    ServerPlayNetworking.send(p, off);
                }
            }
        });

        // Real Monke: track the requester so the hitbox mixin shrinks their box,
        // then refresh dimensions so it applies immediately.
        ServerPlayNetworking.registerGlobalReceiver(
                RealMonkeC2SPayload.ID,
                (payload, context) -> {
                    UUID id = context.player().getUUID();
                    if (payload.enabled()) realMonkePlayers.add(id);
                    else                   realMonkePlayers.remove(id);
                    context.player().refreshDimensions();
                });

        // Monke Model: track + broadcast to everyone so all mod users see it.
        ServerPlayNetworking.registerGlobalReceiver(
                MonkeModelC2SPayload.ID,
                (payload, context) -> {
                    UUID id = context.player().getUUID();
                    if (payload.enabled()) monkeModelPlayers.add(id);
                    else                   monkeModelPlayers.remove(id);
                    MonkeModelS2CPayload sync = new MonkeModelS2CPayload(id, payload.enabled());
                    for (ServerPlayer p : PlayerLookup.all(context.player().level().getServer())) {
                        ServerPlayNetworking.send(p, sync);
                    }
                });
    }
}
