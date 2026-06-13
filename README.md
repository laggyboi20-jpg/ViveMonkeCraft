# ViveMonkeCraft

**Gorilla Tag–style VR locomotion for Minecraft 1.21.4.** Grab the world with your
hands and swing, climb, and fling yourself through it — no joystick required.

Built for **QuestCraft / Vivecraft 1.2.x**. Client-side for singleplayer & LAN; an
optional server companion unlocks it (and lets admins cap it) on dedicated servers.

---

## Requirements

| | |
|---|---|
| Minecraft | 1.21.4 |
| Loader | Fabric Loader 0.19.3+ |
| API | Fabric API |
| VR | QuestCraft / Vivecraft 1.2.x |
| Optional | Mod Menu + Cloth Config (in-game settings screen) |
| Optional | **ViveMonke server mod** (`monke-server`) — only for **dedicated** servers |

The mod is `environment: client`. It does nothing without Vivecraft/QuestCraft active.

---

## Locomotion

- **Hand-grab swinging** — when a hand touches a block it anchors there; moving that
  hand drags your body the opposite way (1:1, no joystick). Two hands average so you
  don't double-move.
- **Throw / jump** — let go while swinging fast and you launch, scaled by *jump power*
  and hard-capped so a tracking glitch can't rocket you.
- **Reach extension** — "gorilla arms" let you touch the ground from standing height
  without physically reaching; your real swing is still measured 1:1 so movement
  never feels twitchy.
- **Climbing & wall behaviour** — cling and climb walls, or set them to slide; a
  slide ramps in over ~½ second so quick pushes keep their momentum but you can't
  hover one-handed.
- **Floor / ground friction & air drag** — fully tunable, from sticky to ice-rink.
- **Gravity multiplier** — from normal all the way to zero-G floating.
- **Step assist** — clear ledges while moving: either an upward boost or an instant
  **step-teleport** onto the block (configurable).
- **Two physics modes:**
  - **Speed-based (default, stable)** — body velocity follows your swing speed.
  - **GT-Physics (Beta)** — a faithful port of the official GorillaLocomotion
    `Player.cs` anchor algorithm: hands plant in world space and your body is dragged
    to keep them there, with configurable drag gain, push strength, unstick distance,
    and ice slip.

## Ice

- Ice is slippery on **every** face — push off ice walls for momentum, but you can't
  cling or climb them.
- Tunable ice speed multiplier kept low for Quest performance.
- **Experimental:** make ice *floors* behave like ice *walls* (pure push-off, no glue).

## Gorilla body ("Real Monke" & Monke Model)

- **Real Monke** — shrinks your collision box to half height (0.5 blocks) so you fit
  through **1-block tunnels**. Collision-only: your model, width, reach and camera
  scale stay normal. Enforced on both client and server so you don't get rubber-banded.
- **Monke Model** — render players **without legs** and with a shortened, tunable
  torso (offset / pitch / scale, plus arm & head offset/rotation, full ±360°). Synced
  so every player with the mod sees every other one legless.
- **Camera height offset** — optionally lower the VR view so your hand hitboxes line
  up with your real hands (authentic GT feel).
- **Hand-model clamping** — your hand models plant on the block surface instead of
  sinking inside it, like GT's hand followers (purely visual; physics uses your real
  hand).

## Comfort & visuals

- **Camera stabilization** — a vignette that narrows your view while moving fast to
  reduce motion sickness (strength configurable).
- **Grip smoothing** — a low-pass filter that removes VR controller jitter from
  locomotion without muting intentional swings.
- **Hand markers** — green/red wireframe cubes + arm lines showing exactly where your
  grab hitboxes are (great for learning; toggle off any time).

## Quality-of-life

- **Auto-enable on join**, with a short grace window so server rules apply first.
- **GUI guard** — opening any screen (inventory, chat, settings…) fully suspends
  locomotion so you never drift while in a menu.
- **Robust teleport handling** — QuestCraft teleports and dimension changes no longer
  leave you stuck, sliding, or wrong-sized.
- **Barrier blocks are ignored** — admins can fence off areas the mod can't bypass.

---

## Controls & configuration

**Toggle the mod:** bind *"ViveMonke(Quest)Craft: Toggle"* in Controls, or to a
Vivecraft radial-menu slot. It auto-enables when you join a world.

**Chat commands** (`/vmc`):

| Command | Effect |
|---|---|
| `/vmc` | Toggle locomotion on/off |
| `/vmc on` · `/vmc off` | Set explicitly |
| `/vmc reload` | Re-read the config file |
| `/vmc set <setting> <value>` | Change **any** setting in-game (tab-completes) |
| `/vmc gravity <0–1>` | Quick gravity set (operator-gated) |

**Settings screen:** with Mod Menu + Cloth Config installed, open the config screen
from the Mods list — sliders & toggles for everything, organized into Presets,
Movement, GT-Physics, Reach & Body, Jump & Gravity, and Visual pages. Changes apply
live. Everything is also editable in `config/vivemonkecraft.properties`.

**Presets:** *Tutorial* (easiest to move), *Default*, *Long Arms*, *Zero Gravity*,
*Speed Run* — and a *Gorilla Tag Feel* setup for the authentic experience.

---

## Multiplayer

- **Singleplayer & "Open to LAN" / Essential** — works with **just this client mod**.
  A LAN host's integrated server authorizes guests and syncs Real Monke / Monke Model
  automatically; no extra download for either player.
- **Dedicated servers** — require the companion **`monke-server`** mod. This is a
  deliberate server-side opt-in (in line with Modrinth's movement-mod policy): the
  mod stays off until the server allows it.

### For server admins (`monke-server`)

Server-side `config/vivemonkecraft-server.properties` lets you:

- **`modEnabled`** — allow or fully disallow VR locomotion (enforced server-side:
  floaters / too-fast players are corrected or kicked).
- **`maxJumpSpeed`** — hard speed cap, backed by per-tick position correction.
- **`opBypassLevel`** — which op level bypasses all restrictions (0–4).
- **Allowances** — *raise* the per-setting caps for everyone on your server (push
  strength, jump multiplier, launch speed, hand reach, arm length, step height, hand
  radius, minimum gravity, minimum air friction). Players without cheats/op are
  otherwise clamped to safe defaults.

---

## Anti-cheat / fair play

Advantage-relevant settings (push, jump, reach, arm length, gravity, etc.) are
**clamped at use-time** to safe defaults for any player without cheats/operator
access. A server can grant higher limits via allowances; operators bypass entirely.
This means editing the config file or sliders can't gain you an unfair edge on a
server that hasn't allowed it.

---

## Building from source

```bash
./gradlew build      # client mod  ->  build/libs/
```

The server companion lives in its own project (`ViveMonke(Quest)Craft-ServerBuild`)
and builds the same way.

---

## License

MIT — see [LICENSE](LICENSE).

*Not affiliated with Another Axiom or Gorilla Tag. "Gorilla Tag" is referenced only to
describe the movement style.*
