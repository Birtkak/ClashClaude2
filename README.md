# Clash Claude

A small Clash Royale–style game for Android, written in Kotlin with Jetpack Compose.

- **Main screen:** build three decks of 8 from 21 cards, see each card's stats, and check your win/loss record.
- **Battle:** a 1v1 match against an AI opponent in a two-lane arena with a river, bridges, princess towers and a king tower.

## Gameplay

- You defend the bottom half. Drag a card onto the arena, or tap a card and then tap the arena.
- Troops and buildings can only be placed on your side, shown by the red overlay. Destroying an enemy princess tower lets you place troops further forward in that lane. Spells can be cast anywhere.
- Elixir regenerates 1 point every 2.8 s, up to 10. It regenerates twice as fast in the last minute and in overtime.
- Each princess tower is worth 1 crown. Destroying the king tower wins the match instantly with 3 crowns.
- Matches last 3 minutes. If crowns are tied, there is 1 minute of sudden-death overtime. If it is still tied after that, the side whose weakest tower has less HP loses.
- The king tower only starts shooting once it takes damage or loses a princess tower.

### Cards

| Troops | | | Spells | Buildings |
|---|---|---|---|---|
| Knight | Archers | Giant | Fireball | Cannon |
| Musketeer | Mini P.E.K.K.A | Goblins | Arrows | Tesla |
| Skeletons | Baby Dragon | Valkyrie | Zap | |
| Hog Rider | Wizard | Minions | | |
| P.E.K.K.A | Bomber | Barbarians | | |
| Spear Goblins | | | | |

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

`playtest/` is a separate desktop build (not part of the app). It compiles the app's
`data`, `game` and `ui` sources against Compose Desktop, with small stand-ins for the few
Android-only APIs. That lets the battle screen run headlessly: it taps and drags cards
and saves screenshots, without needing an emulator.

`.claude/hooks/session-start.sh` installs the Android SDK at the start of Claude Code
cloud sessions, so `./gradlew assembleDebug` works there too.

## Code layout

```
app/src/main/java/com/clashclaude/game/
├── MainActivity.kt       Switches between the home and battle screens
├── data/Cards.kt         Card definitions, default decks and AI decks
├── data/DeckRepository   Saves decks and win/loss stats
├── game/Battle.kt        Simulation: arena, units, targeting, pathing, projectiles, spells, scoring
├── game/Ai.kt            AI opponent
└── ui/                   Compose screens: home/deck builder, battle canvas, card tiles, theme
```

The game engine in `game/` is plain Kotlin with no Android dependencies, so it can be tested on the JVM.
