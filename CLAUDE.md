# Clash Claude

Clash Royale-style Android game: Kotlin + Jetpack Compose. Modules: `:engine` (plain Kotlin game
simulation and card data, shared with a future server), `:app` (Android UI, audio, storage) and
`:baker` (tools/baker: renders 3D models to the sprite sheets the game draws).
See README.md for gameplay, layout and build commands, docs/MULTIPLAYER.md for the server plan and
docs/ART_PIPELINE.md for the art pipeline.

## Checks before pushing

- `./gradlew testDebugUnitTest` (engine tests in `engine/src/test`, plus `DragDeployTest`, which drags a card on the real
  Android Compose stack via Robolectric and saves frames to `app/build/ui-shots/`) and `./gradlew assembleDebug`
- `cd playtest && ../gradlew screenshots` renders the real screens headlessly to
  `playtest/build/playtest/*.png` (sprite gallery, battle, drag ghost, staged fight); look at them
  after any UI or art change.
- `cd playtest && ../gradlew simulate` runs 40 AI matches; `stuckTroops` must stay 0.
- After changing models or `View`: `./gradlew :baker:bake` (commit the regenerated `assets/sprites` and
  `SpriteManifest.kt`) and `./gradlew :baker:test`.
- The `:engine` module can't use Android at all. `app/.../ui/` must not use Android APIs beyond what
  `playtest/src/main/kotlin/stubs` stubs; Android-only code lives in `audio/`, `MainActivity` and `DeckRepository`.
- Keep the engine multiplayer-ready (see docs/MULTIPLAYER.md): only `battle.rng` for randomness,
  player actions go through `Command` + `submit()`, matches advance with `step()`, `MultiplayerTest` stays green.

## Card concepts (Card Forge)

The user submits card ideas through the Card Forge artifact:
https://claude.ai/artifact/BUm139CyGUeFVq3daoNyJw

- Read submissions with the ArtifactData tool: `list` on collection `concepts`.
- Fields: name, type (troop/spell/building), cost, rarity (common/rare/epic/legendary), count, targets,
  range (melee/short/medium/long), speed, hp, damage, hitSpeed, lifetime, radius, flying, splash,
  ability, description, look, notes, images (asset ids, viewable at `/_blob/<id>` on the artifact; fetch
  with the Artifact tool's `read` + `path`), status, claudeNote, createdAt.
- Null stats mean "Claude decides": balance against similar-cost cards in
  `engine/src/main/kotlin/com/clashclaude/game/data/Cards.kt`.
- Status flow, written back with ArtifactData `update` (pin `if_version`):
  `new` (submitted) -> `building` -> `added` (shipped), or `needs-info` with a question in `claudeNote`.
  Put a short note in `claudeNote` when shipping (what changed, any stat tweaks).
- Building a card means: a `CardDef` in `Cards.kt`, a 3D model with the card's id in
  `tools/baker/.../Models.kt` (or a Blender export at `models/<id>.glb`, see docs/ART_PIPELINE.md) baked
  with `./gradlew :baker:bake -Ponly=<id>`, sounds via existing `Sfx` or new ones in `tools/make_sounds.py`, any new
  mechanic in `engine/.../game/Battle.kt` with a unit test in `engine/src/test`, and AI support if the card needs special handling.
- After changing card stats, rebuild the forge page (`python3 tools/card-forge/build.py`) and republish
  `tools/card-forge/card-forge.html` to the URL above so its balance panel stays current.

## Balance changes (Card Forge "Balance cards" tab)

- Read with ArtifactData `list` on collection `balance`; one doc per card id:
  `{cardId, name, changes: {field: newValue}, from: {field: oldValue}, note, status, updatedAt}`.
- Fields map to `CardDef` in `Cards.kt` (`towerDamagePct` is stored as a percent, 35 = 0.35f).
- To apply ("apply the balance changes"): edit `Cards.kt` for every `pending` doc, run tests + simulate,
  then `python3 tools/card-forge/build.py`, `cd playtest && ../gradlew cardImages` (if costs changed),
  republish `tools/card-forge/card-forge.html` with the `cards/<id>.png` files from
  `playtest/build/card-images/` to the Card Forge URL, and set each doc's `status` to `applied`
  (ArtifactData `update`, pinned with `if_version`).
