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

Every push also builds the APK on GitHub Actions. To get it, open the **Actions** tab, pick the latest **Build APK** run, and download the `clash-claude-debug-apk` artifact. To install it on a phone, you'll need to allow installs from unknown sources.

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
