# Multiplayer plan

Goal: friends install the client APK and battle each other. A server on the unRAID box
runs every match, so clients can't cheat and can't drift out of sync.

## Model: server-authoritative

```
 phone A ──commands──▶ ┌─────────────────────────┐ ◀──commands── phone B
         ◀─snapshots── │ server (Docker, unRAID) │ ──snapshots─▶
                       │  one Battle per match   │
                       └─────────────────────────┘
```

- **The server owns the only real `Battle`.** It calls `battle.step()` 30 times a second.
- **Clients send intents and nothing else.** The two intents are `Command.PlayCard(team, cardId, x, y)` and
  `Command.Surrender(team)`. The server stamps each command with the sender's team, so a client
  can't play for the other side, and passes it to `battle.submit()`. `Battle.apply` already
  rejects bad commands: a card that isn't in hand, not enough elixir, or an illegal tile.
- **Clients render snapshots.** About 10 to 15 times a second the server sends the game state
  (units, projectiles, spells, effects, elixir, hands, clock). The client draws between the
  last two snapshots, the same way `ArenaTransform.alpha` blends `prevX`/`x` today.
- **The client never decides anything.** A hacked APK can only send commands the server would
  accept from a fair player anyway.

## What the engine already provides

| Need | Where |
|---|---|
| Engine shared by client and server | `:engine` Gradle module (plain Kotlin/JVM, no Android) |
| Fixed tick | `Battle.step()`, `Battle.TICK_SECONDS` (1/30 s), `Battle.tick` |
| Input as data | `Command` (sealed class), `Battle.submit()` / `apply()` |
| Two humans, no AI | `Battle(..., withAi = false)` |
| Unit ids per match (safe with many matches in one process) | `Combatant.id`, assigned by the battle |
| Results per player | `Battle.outcomeFor(team)`, `surrender(team)` |
| Smooth drawing between ticks | `prevX`/`prevY` on units and projectiles; `ArenaTransform.alpha` |
| Desync detection and debugging | `Battle.checksum()` |
| Seeded randomness | `Battle(rng = Random(seed))`; the engine never uses other randomness |
| Either side at the bottom of the screen | `BattleScreen(viewer = Team.ENEMY)` turns the arena 180° |
| One screen API for local and online play | `Match` interface; `LocalMatch` runs on the device |

`MultiplayerTest` checks that the same seed and commands give the same checksums, that
commands take effect on the next tick, that unit ids are per battle, and that the AI is optional.

## Server sketch

- **Stack:** Kotlin + Ktor (WebSockets) in a new `:server` Gradle module that depends on `:engine`.
  Package it as one Docker image (`eclipse-temurin:17-jre` + a fat jar) and run it as an unRAID
  container with one port mapped, e.g. 8080.
- **Matchmaking v1:** a lobby code. One friend creates a room, the other joins with the code.
  With only friends playing there's no need for accounts; a nickname plus a random device id
  stored in the app is enough. Decks are sent on join and validated: 8 distinct, known card ids.
- **Match loop:** a coroutine per room that ticks `battle.step()` at a fixed rate, drains the
  inbound command queue before each step, and broadcasts a snapshot every 2 or 3 ticks.
- **Reconnects:** keep the room alive for about 30 s; a rejoin gets a full snapshot.
- **Reaching the server from outside:** a forwarded port, or more safely Tailscale or
  Cloudflare Tunnel, so the unRAID box isn't exposed directly.

## Protocol sketch (JSON over WebSocket first; binary later if needed)

```
client → server
  {"t":"join", "room":"ABCD", "name":"Brent", "deck":["knight","archers",...]}
  {"t":"play", "card":"fireball", "x":9.0, "y":8.5, "seq":12}
  {"t":"surrender"}

server → client
  {"t":"start", "you":"PLAYER"|"ENEMY", "opponent":"Sam", "seed":12345}
  {"t":"snap", "tick":930, "time":31.0, "elixir":[4.2,6.1], "crowns":[0,1],
   "hand":["knight",...], "next":"zap",          // only this player's own hand
   "units":[{"id":17,"card":"giant","team":1,"x":4.5,"y":12.0,"hp":2800, ...}],
   "projectiles":[...], "spells":[...], "effects":[...], "sounds":["DEPLOY"]}
  {"t":"reject", "seq":12, "why":"elixir"}
  {"t":"end", "outcome":"WIN", "crowns":[3,1]}
```

- Each client only gets its own hand, so a hacked client can't see the opponent's cards.
- `seq` lets the client undo the card it showed optimistically when the server rejects the play.

## Client work (when the server exists)

1. Add a `NetworkMatch : Match`:
   - `send()` writes the command to the socket.
   - `advance()` returns the interpolation alpha between the last two snapshots.
   - `battle` is a client-side `Battle` that the snapshots overwrite (or a lightweight
     view model that `drawBattle` reads).
2. Main menu: a "Play a friend" button, with create/join room, the server address in
   settings, and the lobby code.
3. Latency hiding: show the deploy ghost and the elixir cost straight away on drop. The units
   themselves appear when the server confirms. The 1.3 s deploy animation already hides
   ~100 ms of round trip.

## Determinism notes

The server is the only source of truth, so client and server don't need bit-identical float
math (JVM vs Android ART). The checksum exists to debug the server, e.g. replaying a recorded
match from its seed plus the command log, and in case lockstep is ever wanted. If you change the
engine, keep it deterministic:

- Use only `battle.rng`. No `Random.Default`, wall-clock time, or `HashMap` iteration order in logic.
- Advance only through `step()`. `update(dt)` with a variable `dt` is for tests and tools.
