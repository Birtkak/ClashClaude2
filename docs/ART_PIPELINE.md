# Art pipeline: 3D models → sprites

The game simulates a flat 2D arena; nothing in the engine knows about 3D. All the 3D lives in
an offline step: models are rendered from the game's fixed camera into sprite sheets, which the
game draws. Units look like smooth 3D models, while the phone only draws images.

```
 model (built-in code, or models/<id>.glb from Blender)
   │  ./gradlew :baker:bake
   ▼
 app/src/main/assets/sprites/<id>_blue.png, <id>_red.png   sprite sheets for both teams
 app/src/main/assets/sprites/card_<id>.png                 card art
 app/src/main/java/.../ui/SpriteManifest.kt                frame layout (generated)
```

## The camera

`engine/.../game/View.kt` defines the camera and is shared by the baker and the renderer:

- Orthographic, looking from the player's side, tilted down `PITCH_DEG` (50°).
- On screen, ground depth is shortened by `View.DEPTH` and heights rise by `View.HEIGHT` per tile.
- Sprites are baked at `PIXELS_PER_TILE` (50).
- Units face 16 directions. Sheets store 9 of them (facing the camera, round through facing
  right, to facing away), and the game mirrors these for the left-facing ones.
- Each direction has idle (4), walk (8) and attack (6) frames. Attack frame 3 is the moment the
  hit lands: the game shows frames 0–2 during the 0.3 s wind-up and 3–5 during the recovery.

If you change the camera, re-bake everything.

## Adding or replacing a model with Blender

1. Model the unit standing at the origin, facing **-Y** (Blender's "front" view). 1 Blender unit
   = 1 arena tile. A knight is about 1.4 tall; the sprite is then drawn 1.35× bigger, like every
   unit in the game.
2. Name the mesh objects whose colour should follow the team `team…` (for example `teamCape`).
   They're tinted blue or red, so one model serves both sides.
3. Add actions named **idle**, **walk** and **attack**:
   - idle and walk should loop;
   - attack should land its hit exactly halfway through the action.

   Each action is stretched over its frames, so the length doesn't matter. Armatures (skinned
   meshes) and plain object animation both work.
4. Colours come from the material's *Base Color*: a colour value, a texture (packed into the
   .glb), or vertex colours. Lighting, toon shading and outlines are added by the baker.
5. Export **glTF Binary (.glb)** with *+Y Up* on and *Animation* on, to `models/<card id>.glb`,
   e.g. `models/knight.glb`.
6. Run `./gradlew :baker:bake -Ponly=knight` and check `cd playtest && ../gradlew screenshots`.
   A .glb in `models/` replaces the built-in model with the same id.

## New cards

A new troop or building card needs a model with the card's id. Spells need an icon model
(`FireballIcon` etc.), used for the card art only. Built-in models are code in
`tools/baker/src/main/kotlin/baker/Models.kt`, using the `Sculpt` primitives and the `Humanoid`
rig in `Rig.kt`. The towers, the king (`kingtop`) and the princess tower archer (the `archers`
sheet) are baked the same way.

## Memory

Frames are trimmed and packed, about 53 MB decoded for all sheets. The battle screen only
loads the sheets for the cards in play. If you add many cards, lower `PIXELS_PER_TILE` or the
frame counts in `View`.
