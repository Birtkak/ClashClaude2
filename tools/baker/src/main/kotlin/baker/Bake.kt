package baker

import com.clashclaude.game.data.CardType
import com.clashclaude.game.data.Cards
import com.clashclaude.game.game.View
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Where the game loads sprites from, and the generated metadata file. */
private val ASSETS = File("app/src/main/assets/sprites")
private val MANIFEST = File("app/src/main/java/com/clashclaude/game/ui/SpriteManifest.kt")

/** Blender exports dropped here (models/<id>.glb) replace the built-in model with that id. */
private val MODELS_DIR = File("models")

private const val MARGIN = 3

/** Portrait (card art) size in pixels; the card face is 0.78 wide per 1 tall. */
private const val PORTRAIT_W = 312
private const val PORTRAIT_H = 400

/**
 * One packed sheet. [frames] holds 6 ints per frame (row-major by direction, then column):
 * atlas x, y, width, height, and the frame's offset from the feet (dx, dy, usually negative).
 */
class SheetInfo(
    val id: String, val directions: Int, val idle: Int, val walk: Int, val attack: Int,
    val mount: Float, val top: Int, val frames: IntArray,
)

/** Packed sheets are at most this wide; frames go on shelves top to bottom. */
private const val ATLAS_WIDTH = 2048

fun main(args: Array<String>) {
    val only = args.indexOf("--only").takeIf { it >= 0 }?.let { args[it + 1].split(",").toSet() }
    ASSETS.mkdirs()
    val cam = Camera(View.PITCH_DEG, View.PIXELS_PER_TILE.toFloat())
    val models = Models.sprites.map { m -> gltfOverride(m) ?: m }

    val infos = ArrayList<SheetInfo>()
    for (model in models) {
        if (only != null && model.id !in only) {
            readExisting(model.id)?.let { infos += it }
            continue
        }
        val t0 = System.currentTimeMillis()
        infos += bakeSheet(model, cam)
        println("baked ${model.id} in ${System.currentTimeMillis() - t0} ms")
    }
    writeManifest(infos)

    for (card in Cards.all) {
        if (only != null && card.id !in only) continue
        bakePortrait(card.id, card.count, card.type)
        println("portrait ${card.id}")
    }
}

/** Uses models/<id>.glb when present. */
private fun gltfOverride(m: SpriteModel): SpriteModel? {
    val f = File(MODELS_DIR, "${m.id}.glb")
    if (!f.exists()) return null
    println("using ${f.path} for ${m.id}")
    return GltfModel.load(m.id, f, m.idleFrames, m.walkFrames, m.attackFrames, m.directions, m.mount, m.scale)
}

/** The poses of one sheet row, left to right: idle, walk, attack. */
fun framesOf(m: SpriteModel): List<Pose> {
    val out = ArrayList<Pose>()
    for (i in 0 until m.idleFrames) out += Pose(Anim.IDLE, i / m.idleFrames.toFloat())
    for (i in 0 until m.walkFrames) out += Pose(Anim.WALK, i / m.walkFrames.toFloat())
    val span = View.ATTACK_WINDUP + View.ATTACK_RECOVER
    // With View.ATTACK_FRAMES frames, frame View.ATTACK_HIT_FRAME lands exactly on t = 0 (the hit).
    for (k in 0 until m.attackFrames) out += Pose(Anim.ATTACK, attackT = -View.ATTACK_WINDUP + k * span / m.attackFrames)
    return out
}

private fun headings(m: SpriteModel): List<Float> =
    if (m.directions == 1) listOf((PI / 2).toFloat()) else List(m.directions) { View.headingOfRow(it) }

private fun bakeSheet(model: SpriteModel, cam: Camera): SheetInfo {
    val poses = framesOf(model)
    val teams = if (model.teamless) listOf(TeamColor.BLUE) else TeamColor.entries
    // Build every pose once per team; the soup is the same for every facing.
    val soups = teams.associateWith { team -> poses.map { p -> Sculpt().also { model.build(it, p, team) }.soup } }
    val dirs = headings(model)
    val worlds = dirs.map { placeInWorld(it) * Mat4.scale(model.scale) }
    val b = Bounds()
    for (list in soups.values) for (soup in list) for (w in worlds) b.add(bounds(soup, w, cam))
    val anchorX = (-floor(b.minX)).toInt() + MARGIN
    val anchorY = (-floor(b.minY)).toInt() + MARGIN
    val cellW = (ceil(b.maxX) - floor(b.minX)).toInt() + 2 * MARGIN
    val cellH = (ceil(b.maxY) - floor(b.minY)).toInt() + 2 * MARGIN

    // Render every frame for both teams, then trim each to the pixels it actually uses.
    class Frame(val px: Map<TeamColor, IntArray>, val x0: Int, val y0: Int, val w: Int, val h: Int) {
        var ax = 0
        var ay = 0
    }
    val frames = ArrayList<Frame>()
    for (w in worlds) for (i in poses.indices) {
        val px = teams.associateWith { render(soups[it]!![i], w, cam, cellW, cellH, anchorX.toFloat(), anchorY.toFloat()) }
        var x0 = cellW; var y0 = cellH; var x1 = -1; var y1 = -1
        for (img in px.values) for (y in 0 until cellH) for (x in 0 until cellW) {
            if (img[y * cellW + x] ushr 24 == 0) continue
            x0 = min(x0, x); y0 = min(y0, y); x1 = max(x1, x); y1 = max(y1, y)
        }
        if (x1 < 0) { x0 = 0; y0 = 0; x1 = 0; y1 = 0 }
        frames += Frame(px, x0, y0, x1 - x0 + 1, y1 - y0 + 1)
    }

    // Shelf packing, tallest frames first.
    var x = 0; var y = 0; var shelf = 0
    for (f in frames.sortedByDescending { it.h }) {
        if (x + f.w > ATLAS_WIDTH) { x = 0; y += shelf + 1; shelf = 0 }
        f.ax = x; f.ay = y
        x += f.w + 1
        shelf = max(shelf, f.h)
    }
    val width = min(ATLAS_WIDTH, frames.sumOf { it.w + 1 })
    val height = y + shelf
    for (team in teams) {
        val sheet = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        for (f in frames) {
            val src = f.px[team]!!
            for (r in 0 until f.h) sheet.setRGB(f.ax, f.ay + r, f.w, 1, src, (f.y0 + r) * cellW + f.x0, cellW)
        }
        ImageIO.write(sheet, "png", File(ASSETS, "${model.id}_${team.name.lowercase()}.png"))
    }
    val table = IntArray(frames.size * 6)
    for ((i, f) in frames.withIndex()) {
        table[i * 6] = f.ax; table[i * 6 + 1] = f.ay; table[i * 6 + 2] = f.w; table[i * 6 + 3] = f.h
        table[i * 6 + 4] = f.x0 - anchorX; table[i * 6 + 5] = f.y0 - anchorY
    }
    val top = frames.maxOf { anchorY - it.y0 }
    println("  ${model.id}: ${frames.size} frames packed into ${width}x$height (cells were ${cellW}x$cellH)")
    return SheetInfo(model.id, dirs.size, model.idleFrames, model.walkFrames, model.attackFrames, model.mount * model.scale, top, table)
}

/** Card art: the unit (or a small group of them) seen from lower and closer, fitted to the card. */
private fun bakePortrait(id: String, count: Int, type: CardType) {
    val model = Models.byId(id)?.let { gltfOverride(it) ?: it } ?: return
    val soup = Soup()
    val sculpt = Sculpt(soup)
    val pose = Pose.REST
    // Small groups stand in a staggered line; the nearest one in front.
    val spots = when (count) {
        1 -> listOf(0f to 0f)
        2 -> listOf(-0.32f to -0.25f, 0.3f to 0.15f)
        else -> listOf(-0.42f to -0.3f, 0.42f to -0.3f, 0f to 0.2f)
    }
    for ((x, z) in spots) sculpt.at(x, 0f, z) { model.build(this, pose, TeamColor.BLUE) }
    // (Portraits are fitted to the card, so the model's in-game scale doesn't matter here.)
    val cam0 = Camera(if (type == CardType.SPELL) 12f else 18f, 1f)
    val heading = (PI / 2 - if (type == CardType.SPELL) 0.0 else 0.5).toFloat()
    val world = placeInWorld(heading)
    val b = bounds(soup, world, cam0)
    val pad = 18f
    val ppt = min((PORTRAIT_W - 2 * pad) / (b.maxX - b.minX), (PORTRAIT_H * 0.82f - pad) / (b.maxY - b.minY))
    val cam = Camera(cam0.pitchDeg, ppt)
    val ax = PORTRAIT_W / 2f - (b.minX + b.maxX) / 2f * ppt
    // Feet near the bottom, above the name banner.
    val ay = PORTRAIT_H * 0.84f - b.maxY * ppt
    val px = render(soup, world, cam, PORTRAIT_W, PORTRAIT_H, ax, ay, ss = 3, outlinePx = 3.2f)
    val img = BufferedImage(PORTRAIT_W, PORTRAIT_H, BufferedImage.TYPE_INT_ARGB)
    img.setRGB(0, 0, PORTRAIT_W, PORTRAIT_H, px, 0, PORTRAIT_W)
    ImageIO.write(img, "png", File(ASSETS, "card_$id.png"))
}

private fun readExisting(id: String): SheetInfo? {
    if (!MANIFEST.exists()) return null
    val line = MANIFEST.readLines().firstOrNull { it.contains("\"$id\" to SpriteSheet(") } ?: return null
    val args = line.substringAfter("SpriteSheet(").substringBefore(", \"").split(",").map { it.trim().removeSuffix("f") }
    val table = line.substringAfter(", \"").substringBefore("\"").split(" ").filter { it.isNotEmpty() }.map { it.toInt() }
    return SheetInfo(id, args[0].toInt(), args[1].toInt(), args[2].toInt(), args[3].toInt(), args[4].toFloat(), args[5].toInt(), table.toIntArray())
}

private fun writeManifest(infos: List<SheetInfo>) {
    val sb = StringBuilder()
    sb.appendLine("package com.clashclaude.game.ui")
    sb.appendLine()
    sb.appendLine("// Generated by tools/baker (./gradlew :baker:bake). Do not edit by hand.")
    sb.appendLine()
    sb.appendLine("/**")
    sb.appendLine(" * Every baked sprite sheet in assets/sprites/<id>_<team>.png: directions, idle/walk/attack")
    sb.appendLine(" * frame counts, tower mount height, tallest frame above the feet, and the packed frame table.")
    sb.appendLine(" */")
    sb.appendLine("internal val SpriteManifest: Map<String, SpriteSheet> = mapOf(")
    for (i in infos) {
        sb.appendLine(
            "    \"${i.id}\" to SpriteSheet(${i.directions}, ${i.idle}, ${i.walk}, ${i.attack}, ${i.mount}f, ${i.top}, " +
                "\"${i.frames.joinToString(" ")}\"),",
        )
    }
    sb.appendLine(")")
    MANIFEST.writeText(sb.toString())
    var bytes = 0L
    ASSETS.listFiles()?.forEach { bytes += it.length() }
    var pixels = 0L
    for (f in ASSETS.listFiles() ?: emptyArray()) {
        if (f.name.startsWith("card_")) continue
        val img = ImageIO.read(f)
        pixels += img.width.toLong() * img.height
    }
    println("manifest: ${infos.size} sheets, ${bytes / 1024} KB of PNGs, ${pixels * 4 / 1024 / 1024} MB decoded")
}
