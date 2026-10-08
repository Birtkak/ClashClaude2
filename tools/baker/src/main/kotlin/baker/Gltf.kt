package baker

import com.clashclaude.game.game.View
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.ImageIO

/**
 * A model loaded from a binary glTF (.glb), e.g. exported from Blender.
 *
 * Conventions (see docs/ART_PIPELINE.md):
 * - 1 unit = 1 arena tile, feet on the ground at the origin, facing +Z in glTF (Blender: -Y).
 * - Animations named "idle", "walk" and "attack" (case-insensitive; "run" also counts as walk).
 *   Idle and walk loop over their whole length; the attack clip should land its hit halfway.
 * - Materials use the base colour (factor and/or texture); vertex colours are multiplied in.
 * - Rigid node animation and skinned meshes (armatures) are both supported.
 * - Mesh names starting with "team" are tinted with the team colour (so one model serves both sides).
 */
class GltfModel private constructor(
    id: String, idle: Int, walk: Int, attack: Int, dirs: Int, override val mount: Float, override val scale: Float,
    private val json: JsonObject, private val bin: ByteBuffer,
) : SpriteModel(id, idle, walk, attack, dirs) {

    private val nodes = json.arr("nodes")
    private val textures = HashMap<Int, Texture?>()
    private val clips = json.arr("animations").map { it.asJsonObject }

    private fun clip(anim: Anim): JsonObject? {
        val wanted = when (anim) {
            Anim.IDLE -> listOf("idle")
            Anim.WALK -> listOf("walk", "run")
            Anim.ATTACK -> listOf("attack")
        }
        return clips.firstOrNull { c -> val n = c.str("name").lowercase(); wanted.any { n.contains(it) } }
    }

    override fun build(s: Sculpt, p: Pose, team: TeamColor) {
        // Sample the clip for this pose into per-node TRS overrides.
        val clip = clip(p.anim) ?: clip(Anim.IDLE)
        val time = clip?.let { c ->
            val len = clipLength(c)
            when (p.anim) {
                Anim.ATTACK -> ((p.attackT + View.ATTACK_WINDUP) / (View.ATTACK_WINDUP + View.ATTACK_RECOVER)) * len
                else -> p.phase * len
            }
        } ?: 0f
        val trs = Array(nodes.size()) { i -> nodeTrs(nodes[i].asJsonObject) }
        if (clip != null) applyClip(clip, time, trs)
        val global = arrayOfNulls<Mat4>(nodes.size())
        // glTF's +X is the model's left when it faces +Z; mirror to our right-handed-for-sprites space.
        val root = Mat4.scale(-1f, 1f, 1f)
        val scene = json.arr("scenes").let { if (it.size() > 0) it[json.get("scene")?.asInt ?: 0].asJsonObject.arr("nodes") else JsonArray() }
        fun walk(i: Int, parent: Mat4) {
            val m = parent * trs[i].matrix()
            global[i] = m
            nodes[i].asJsonObject.arr("children").forEach { walk(it.asInt, m) }
        }
        val roots = if (scene.size() > 0) scene.map { it.asInt } else (0 until nodes.size()).filter { i -> nodes.none { n -> n.asJsonObject.arr("children").any { it.asInt == i } } }
        roots.forEach { walk(it, Mat4()) }
        for (i in 0 until nodes.size()) {
            val node = nodes[i].asJsonObject
            val meshIdx = node.get("mesh")?.asInt ?: continue
            val g = global[i] ?: continue
            val skin = node.get("skin")?.asInt?.let { json.arr("skins")[it].asJsonObject }
            emitMesh(s, root, json.arr("meshes")[meshIdx].asJsonObject, g, skin, global, team)
        }
    }

    private fun emitMesh(s: Sculpt, root: Mat4, mesh: JsonObject, world: Mat4, skin: JsonObject?, global: Array<Mat4?>, team: TeamColor) {
        val tinted = mesh.str("name").lowercase().startsWith("team")
        val jointMats: List<Mat4>? = skin?.let { sk ->
            val joints = sk.arr("joints").map { it.asInt }
            val ibm = sk.get("inverseBindMatrices")?.asInt?.let { readFloats(it) }
            joints.mapIndexed { j, node ->
                val inv = if (ibm != null) Mat4(FloatArray(16) { k -> ibm[j * 16 + (k % 4) * 4 + k / 4] }) else Mat4()
                (global[node] ?: Mat4()) * inv
            }
        }
        for (prim in mesh.arr("primitives")) {
            val pr = prim.asJsonObject
            if ((pr.get("mode")?.asInt ?: 4) != 4) continue
            val attrs = pr.getAsJsonObject("attributes")
            val pos = readFloats(attrs.get("POSITION").asInt)
            val nrm = attrs.get("NORMAL")?.asInt?.let { readFloats(it) }
            val uvs = attrs.get("TEXCOORD_0")?.asInt?.let { readFloats(it) }
            val cols = attrs.get("COLOR_0")?.asInt?.let { readFloats(it) to accessorWidth(it) }
            val joints = attrs.get("JOINTS_0")?.asInt?.let { readFloats(it) }
            val weights = attrs.get("WEIGHTS_0")?.asInt?.let { readFloats(it) }
            val count = pos.size / 3
            val idx = pr.get("indices")?.asInt?.let { readFloats(it).map { f -> f.toInt() } } ?: (0 until count).toList()

            var base = 0xFFFFFF
            var tex: Texture? = null
            pr.get("material")?.asInt?.let { mi ->
                val pbr = json.arr("materials")[mi].asJsonObject.getAsJsonObject("pbrMetallicRoughness")
                pbr?.getAsJsonArray("baseColorFactor")?.let { f ->
                    base = rgb(f[0].asFloat, f[1].asFloat, f[2].asFloat)
                }
                pbr?.getAsJsonObject("baseColorTexture")?.get("index")?.asInt?.let { tex = texture(it) }
            }
            if (tinted) base = team.main

            // Transform every vertex (skinned or rigid) into model space.
            val p = FloatArray(count * 3)
            val n = FloatArray(count * 3)
            val tmp = FloatArray(3)
            for (v in 0 until count) {
                val x = pos[v * 3]; val y = pos[v * 3 + 1]; val z = pos[v * 3 + 2]
                val nx = nrm?.get(v * 3) ?: 0f; val ny = nrm?.get(v * 3 + 1) ?: 1f; val nz = nrm?.get(v * 3 + 2) ?: 0f
                if (jointMats != null && joints != null && weights != null) {
                    var ox = 0f; var oy = 0f; var oz = 0f; var onx = 0f; var ony = 0f; var onz = 0f
                    for (k in 0 until 4) {
                        val w = weights[v * 4 + k]
                        if (w == 0f) continue
                        val jm = jointMats[joints[v * 4 + k].toInt()]
                        ox += w * jm.px(x, y, z); oy += w * jm.py(x, y, z); oz += w * jm.pz(x, y, z)
                        jm.normal(nx, ny, nz, tmp, 0)
                        onx += w * tmp[0]; ony += w * tmp[1]; onz += w * tmp[2]
                    }
                    p[v * 3] = ox; p[v * 3 + 1] = oy; p[v * 3 + 2] = oz
                    n[v * 3] = onx; n[v * 3 + 1] = ony; n[v * 3 + 2] = onz
                } else {
                    p[v * 3] = world.px(x, y, z); p[v * 3 + 1] = world.py(x, y, z); p[v * 3 + 2] = world.pz(x, y, z)
                    world.normal(nx, ny, nz, n, v * 3)
                }
            }
            if (uvs != null && tex != null) s.soup.enableUv()
            for (t in 0 until idx.size / 3) {
                var r = 0f; var g = 0f; var b = 0f
                for (c in 0 until 3) {
                    val v = idx[t * 3 + c]
                    s.soup.addVertex(root, p[v * 3], p[v * 3 + 1], p[v * 3 + 2], n[v * 3], n[v * 3 + 1], n[v * 3 + 2],
                        uvs?.get(v * 2) ?: 0f, uvs?.get(v * 2 + 1) ?: 0f)
                    if (cols != null) { val w = cols.second; r += cols.first[v * w]; g += cols.first[v * w + 1]; b += cols.first[v * w + 2] }
                }
                val color = if (cols != null && !tinted) multiply(base, rgb(r / 3, g / 3, b / 3)) else base
                s.soup.endTriangle(Mat(color), if (uvs != null) tex else null)
            }
        }
    }

    // ------------------------------------------------------------ animation

    private class Trs(var t: FloatArray, var r: FloatArray, var s: FloatArray, val fixed: Mat4?) {
        fun matrix() = fixed ?: (Mat4.translate(t[0], t[1], t[2]) * Mat4.quat(r[0], r[1], r[2], r[3]) * Mat4.scale(s[0], s[1], s[2]))
    }

    private fun nodeTrs(n: JsonObject): Trs {
        n.getAsJsonArray("matrix")?.let { m ->
            // glTF matrices are column-major.
            return Trs(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f), floatArrayOf(1f, 1f, 1f), Mat4(FloatArray(16) { k -> m[(k % 4) * 4 + k / 4].asFloat }))
        }
        fun arr(name: String, def: FloatArray) = n.getAsJsonArray(name)?.let { a -> FloatArray(a.size()) { a[it].asFloat } } ?: def
        return Trs(arr("translation", floatArrayOf(0f, 0f, 0f)), arr("rotation", floatArrayOf(0f, 0f, 0f, 1f)), arr("scale", floatArrayOf(1f, 1f, 1f)), null)
    }

    private fun clipLength(c: JsonObject): Float =
        c.arr("samplers").maxOfOrNull { s -> readFloats(s.asJsonObject.get("input").asInt).maxOrNull() ?: 0f } ?: 0f

    private fun applyClip(c: JsonObject, time: Float, trs: Array<Trs>) {
        val samplers = c.arr("samplers")
        for (ch in c.arr("channels")) {
            val chan = ch.asJsonObject
            val target = chan.getAsJsonObject("target")
            val node = target.get("node")?.asInt ?: continue
            val path = target.str("path")
            val smp = samplers[chan.get("sampler").asInt].asJsonObject
            val times = readFloats(smp.get("input").asInt)
            val values = readFloats(smp.get("output").asInt)
            val width = when (path) { "rotation" -> 4; "translation", "scale" -> 3; else -> continue }
            val cubic = smp.str("interpolation") == "CUBICSPLINE"
            val stride = if (cubic) width * 3 else width
            val off = if (cubic) width else 0
            var k = 0
            while (k < times.size - 1 && times[k + 1] <= time) k++
            val k2 = minOf(k + 1, times.size - 1)
            val f = if (k2 == k || time <= times[k]) 0f else ((time - times[k]) / (times[k2] - times[k])).coerceIn(0f, 1f)
            val step = smp.str("interpolation") == "STEP"
            val out = FloatArray(width) { i ->
                val a = values[k * stride + off + i]; val b = values[k2 * stride + off + i]
                if (step) a else a + (b - a) * f
            }
            if (path == "rotation") {
                val l = kotlin.math.sqrt(out.sumOf { (it * it).toDouble() }).toFloat().coerceAtLeast(1e-6f)
                for (i in 0 until 4) out[i] /= l
            }
            when (path) {
                "rotation" -> trs[node].r = out
                "translation" -> trs[node].t = out
                "scale" -> trs[node].s = out
            }
        }
    }

    // ------------------------------------------------------------ buffers

    private fun accessorWidth(i: Int) = when (json.arr("accessors")[i].asJsonObject.str("type")) {
        "SCALAR" -> 1; "VEC2" -> 2; "VEC3" -> 3; "VEC4" -> 4; "MAT4" -> 16; else -> 1
    }

    /** Reads any accessor as floats (normalised integer types are scaled to 0..1). */
    private fun readFloats(i: Int): FloatArray {
        val acc = json.arr("accessors")[i].asJsonObject
        val count = acc.get("count").asInt
        val width = accessorWidth(i)
        val type = acc.get("componentType").asInt
        val normalized = acc.get("normalized")?.asBoolean ?: false
        val out = FloatArray(count * width)
        val bvIdx = acc.get("bufferView")?.asInt ?: return out
        val bv = json.arr("bufferViews")[bvIdx].asJsonObject
        val compSize = when (type) { 5120, 5121 -> 1; 5122, 5123 -> 2; else -> 4 }
        val stride = bv.get("byteStride")?.asInt ?: (compSize * width)
        val start = (bv.get("byteOffset")?.asInt ?: 0) + (acc.get("byteOffset")?.asInt ?: 0)
        for (e in 0 until count) for (c in 0 until width) {
            val at = start + e * stride + c * compSize
            out[e * width + c] = when (type) {
                5126 -> bin.getFloat(at)
                5125 -> bin.getInt(at).toFloat()
                5123 -> (bin.getShort(at).toInt() and 0xFFFF).let { if (normalized) it / 65535f else it.toFloat() }
                5122 -> bin.getShort(at).toFloat().let { if (normalized) it / 32767f else it }
                5121 -> (bin.get(at).toInt() and 0xFF).let { if (normalized) it / 255f else it.toFloat() }
                else -> bin.get(at).toFloat().let { if (normalized) it / 127f else it }
            }
        }
        return out
    }

    private fun texture(i: Int): Texture? = textures.getOrPut(i) {
        val src = json.arr("textures")[i].asJsonObject.get("source")?.asInt ?: return@getOrPut null
        val img = json.arr("images")[src].asJsonObject
        val bv = json.arr("bufferViews")[img.get("bufferView")?.asInt ?: return@getOrPut null].asJsonObject
        val bytes = ByteArray(bv.get("byteLength").asInt)
        bin.position(bv.get("byteOffset")?.asInt ?: 0)
        bin.get(bytes)
        bin.position(0)
        val bi = ImageIO.read(ByteArrayInputStream(bytes)) ?: return@getOrPut null
        Texture(bi.width, bi.height, bi.getRGB(0, 0, bi.width, bi.height, null, 0, bi.width))
    }

    companion object {
        fun load(id: String, file: File, idle: Int, walk: Int, attack: Int, dirs: Int, mount: Float, scale: Float = 1f): GltfModel {
            val buf = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
            require(buf.getInt(0) == 0x46546C67) { "${file.name} is not a binary glTF (.glb) file" }
            var at = 12
            var json: JsonObject? = null
            var bin: ByteBuffer = ByteBuffer.allocate(0)
            while (at < buf.limit()) {
                val len = buf.getInt(at); val type = buf.getInt(at + 4)
                val chunk = ByteArray(len)
                buf.position(at + 8); buf.get(chunk)
                when (type) {
                    0x4E4F534A -> json = JsonParser.parseString(String(chunk, Charsets.UTF_8)).asJsonObject
                    0x004E4942 -> bin = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN)
                }
                at += 8 + len
            }
            return GltfModel(id, idle, walk, attack, dirs, mount, scale, json ?: error("no JSON chunk in ${file.name}"), bin)
        }
    }
}

private fun JsonObject.arr(name: String): JsonArray = getAsJsonArray(name) ?: JsonArray()
private fun JsonObject.str(name: String): String = get(name)?.asString ?: ""

private fun rgb(r: Float, g: Float, b: Float): Int {
    // glTF colours are linear; convert to sRGB for display.
    fun ch(v: Float) = (Math.pow(v.coerceIn(0f, 1f).toDouble(), 1 / 2.2) * 255 + 0.5).toInt().coerceIn(0, 255)
    return (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
}

private fun multiply(a: Int, b: Int): Int {
    val r = ((a shr 16) and 255) * ((b shr 16) and 255) / 255
    val g = ((a shr 8) and 255) * ((b shr 8) and 255) / 255
    val bl = (a and 255) * (b and 255) / 255
    return (r shl 16) or (g shl 8) or bl
}
