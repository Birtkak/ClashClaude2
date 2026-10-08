# Clash Claude

A small Clash Royale–style game for Android, written in Kotlin with Jetpack Compose.

- **Main screen:** build three decks of 8 from 28 cards, see each card's stats, and check your win/loss record.
- **Battle:** a 1v1 match against an AI opponent in a two-lane arena with a river, bridges, princess towers and a king tower.

## Gameplay

- You defend the bottom half. Drag a card onto the arena, or tap a card and then tap the arena.
- Troops and buildings can only be placed on your side, shown by the red overlay. Destroying an enemy princess tower lets you place troops further forward in that lane. Spells can be cast anywhere.
- Elixir regenerates 1 point every 2.8 s, up to 10. It regenerates twice as fast in the last minute and in overtime.
- Each princess tower is worth 1 crown. Destroying the king tower wins the match instantly with 3 crowns.
- Matches last 3 minutes. If crowns are tied, there is 1 minute of sudden-death overtime. If it is still tied after that, the side whose weakest tower has less HP loses.
- The king tower only starts shooting once it takes damage or loses a princess tower.

### Cards (28)

- **Troops:** Knight, Archers, Giant, Royal Giant, Musketeer, Mini P.E.K.K.A, P.E.K.K.A, Goblins, Spear Goblins,
  Skeletons, Bomber, Barbarians, Valkyrie, Hog Rider, Wizard, Witch (summons skeletons), Baby Dragon, Minions, Mega Minion
- **Spells:** Fireball, Arrows, Zap, Freeze (freezes troops), Lightning (strikes the 3 toughest enemies)
- **Buildings:** Cannon, Tesla, Inferno Tower (damage ramps up), Tombstone (spawns skeletons)

The Deck tab's **Magic deck** button builds a random deck with one win condition, one or two spells,
anti-air, at most one building, cheap cycle cards and a sensible elixir average.

## Building

Open the project in Android Studio and press **Run**, or from the command line:

```
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Every push also builds the APK on GitHub Actions and publishes it as a release. Download the newest build on your phone from:

https://github.com/Birtkak/ClashClaude2/releases/latest/download/ClashClaude.apk

To install it, open the downloaded file and allow installs from that app when Android asks.

## Testing and playtesting

```
./gradlew testDebugUnitTest            # engine unit tests (placement, elixir, cycling, full matches)
./gradlew lintDebug                    # Android lint
cd playtest && ../gradlew screenshots  # scripted playthrough of the real screens -> playtest/build/playtest/*.png
cd playtest && ../gradlew simulate     # 40 headless matches of the AI vs a random bot, with sanity checks
```

`playtest/` is a separate desktop build (not part of the app). It compiles the engine and the app's
`data` and `ui` sources against Compose Desktop, with small stand-ins for the few
Android-only APIs. That lets the battle screen run headlessly: it taps and drags cards
and saves screenshots, without needing an emulator.

`.claude/hooks/session-start.sh` installs the Android SDK at the start of Claude Code
cloud sessions, so `./gradlew assembleDebug` works there too.

## Code layout

```
engine/src/main/kotlin/com/clashclaude/game/     Plain Kotlin, no Android: shared with a future server
├── data/Cards.kt         Card definitions, default decks and AI decks
├── data/DeckBuilder.kt   Random-but-sensible decks for the Magic button
├── game/Battle.kt        Simulation: arena, units, targeting, pathing, projectiles, spells, scoring
├── game/Command.kt       Player actions (play card, surrender) as data
├── game/Match.kt         What the battle screen drives; LocalMatch runs the battle on the phone
├── game/Ai.kt            AI opponent
└── game/Pathfinder.kt    A* routing around towers and buildings

app/src/main/java/com/clashclaude/game/
├── MainActivity.kt       Switches between the home and battle screens
├── data/DeckRepository   Saves decks and win/loss stats
├── audio/                Sound effects and music
└── ui/                   Compose screens: home/deck builder, battle canvas and HUD, sprites, theme
```

The engine runs in fixed 1/30 s ticks (`Battle.step()`), takes player input only as `Command`s,
and is deterministic for a seed. The battle screen can show either side at the bottom. That's the
groundwork for online play against friends, with a server on unRAID running the real match.
See [docs/MULTIPLAYER.md](docs/MULTIPLAYER.md).
