# Clash Claude

Clash Royale-style Android game: Kotlin + Jetpack Compose, single `app` module.
See README.md for gameplay, layout and build commands.

## Checks before pushing

- `./gradlew testDebugUnitTest` (engine tests) and `./gradlew assembleDebug`
- `cd playtest && ../gradlew screenshots` renders the real screens headlessly to
  `playtest/build/playtest/*.png` (sprite gallery, battle, drag ghost, staged fight); look at them
  after any UI or art change.
- `cd playtest && ../gradlew simulate` runs 40 AI matches; `stuckTroops` must stay 0.
- The engine (`game/`) and `ui/` must not use Android APIs beyond what `playtest/src/main/kotlin/stubs`
  stubs; Android-only code lives in `audio/`, `MainActivity` and `DeckRepository`.

## Card concepts (Card Forge)

The user submits card ideas through the Card Forge artifact:
https://claude.ai/artifact/BUm139CyGUeFVq3daoNyJw

- Read submissions with the ArtifactData tool: `list` on collection `concepts`.
- Fields: name, type (troop/spell/building), cost, rarity (common/rare/epic/legendary), count, targets,
  range (melee/short/medium/long), speed, hp, damage, hitSpeed, lifetime, radius, flying, splash,
  ability, description, look, notes, images (asset ids, viewable at `/_blob/<id>` on the artifact; fetch
  with the Artifact tool's `read` + `path`), status, claudeNote, createdAt.
- Null stats mean "Claude decides": balance against similar-cost cards in `data/Cards.kt`.
- Status flow, written back with ArtifactData `update` (pin `if_version`):
  `new` (submitted) -> `building` -> `added` (shipped), or `needs-info` with a question in `claudeNote`.
  Put a short note in `claudeNote` when shipping (what changed, any stat tweaks).
- Building a card means: a `CardDef` in `Cards.kt`, sprite art in `ui/Art.kt` (`Pen.unit` branch and,
  if needed, `spriteTop`), sounds via existing `Sfx` or new ones in `tools/make_sounds.py`, any new
  mechanic in `game/Battle.kt` with a unit test, and AI support if the card needs special handling.
- After changing card stats, rebuild the forge page (`python3 tools/card-forge/build.py`) and republish
  `tools/card-forge/card-forge.html` to the URL above so its balance panel stays current.
