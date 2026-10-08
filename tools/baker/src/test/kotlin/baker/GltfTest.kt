package baker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Loads a tiny skinned, animated .glb built in code, the way a Blender export would arrive. */
class GltfTest {
    /** One triangle skinned to joint 1, which a "walk" clip slides up by one unit over one second. */
    private fun writeGlb(): File {
        val bin = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN)
        // 0: positions (3 x vec3)
        for (v in floatArrayOf(0f, 1f, 0f, 0.5f, 1f, 0f, 0f, 1.5f, 0f)) bin.putFloat(v)
        // 36: joints (3 x 4 ubyte) all on joint 1
        repeat(3) { bin.put(1); bin.put(0); bin.put(0); bin.put(0) }
        // 48: weights (3 x vec4)
        repeat(3) { bin.putFloat(1f); bin.putFloat(0f); bin.putFloat(0f); bin.putFloat(0f) }
        // 96: times
        bin.putFloat(0f); bin.putFloat(1f)
        // 104: translations
        for (v in floatArrayOf(0f, 1f, 0f, 0f, 2f, 0f)) bin.putFloat(v)
        // 128: inverse bind matrices (column-major): identity, translate(0, -1, 0)
        for (v in floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)) bin.putFloat(v)
        for (v in floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, -1f, 0f, 1f)) bin.putFloat(v)
        val json = """
        {"asset":{"version":"2.0"},"scene":0,"scenes":[{"nodes":[0,2]}],
         "nodes":[{"name":"root","children":[1]},{"name":"arm","translation":[0,1,0]},{"mesh":0,"skin":0}],
         "skins":[{"joints":[0,1],"inverseBindMatrices":5}],
         "meshes":[{"name":"teamCloth","primitives":[{"attributes":{"POSITION":0,"JOINTS_0":1,"WEIGHTS_0":2}}]}],
         "animations":[{"name":"Walk","channels":[{"sampler":0,"target":{"node":1,"path":"translation"}}],
                        "samplers":[{"input":3,"output":4}]}],
         "buffers":[{"byteLength":256}],
         "bufferViews":[{"buffer":0,"byteOffset":0,"byteLength":36},{"buffer":0,"byteOffset":36,"byteLength":12},
                        {"buffer":0,"byteOffset":48,"byteLength":48},{"buffer":0,"byteOffset":96,"byteLength":8},
                        {"buffer":0,"byteOffset":104,"byteLength":24},{"buffer":0,"byteOffset":128,"byteLength":128}],
         "accessors":[{"bufferView":0,"componentType":5126,"count":3,"type":"VEC3"},
                      {"bufferView":1,"componentType":5121,"count":3,"type":"VEC4"},
                      {"bufferView":2,"componentType":5126,"count":3,"type":"VEC4"},
                      {"bufferView":3,"componentType":5126,"count":2,"type":"SCALAR"},
                      {"bufferView":4,"componentType":5126,"count":2,"type":"VEC3"},
                      {"bufferView":5,"componentType":5126,"count":2,"type":"MAT4"}]}
        """.trimIndent().let { it + " ".repeat((4 - it.length % 4) % 4) }.toByteArray()
        val out = ByteBuffer.allocate(12 + 8 + json.size + 8 + 256).order(ByteOrder.LITTLE_ENDIAN)
        out.putInt(0x46546C67); out.putInt(2); out.putInt(out.capacity())
        out.putInt(json.size); out.putInt(0x4E4F534A); out.put(json)
        out.putInt(256); out.putInt(0x004E4942); out.put(bin.array())
        return File.createTempFile("test", ".glb").apply { writeBytes(out.array()); deleteOnExit() }
    }

    private fun build(model: GltfModel, pose: Pose, team: TeamColor = TeamColor.BLUE) = Sculpt().also { model.build(it, pose, team) }.soup

    @Test
    fun skinnedAnimationMovesTheMesh() {
        val model = GltfModel.load("test", writeGlb(), 1, 4, 0, 9, 0f)
        val rest = build(model, Pose(Anim.WALK, 0f))
        val mid = build(model, Pose(Anim.WALK, 0.5f))
        assertEquals(1, rest.tris)
        assertEquals("bind pose", 1f, rest.pos[1], 1e-4f)
        assertEquals("half way through the clip the joint has moved up 0.5", 1.5f, mid.pos[1], 1e-4f)
        // glTF +X is the model's left: it is mirrored into sprite space.
        assertEquals(-0.5f, rest.pos[3], 1e-4f)
    }

    @Test
    fun teamMeshesTakeTheTeamColour() {
        val model = GltfModel.load("test", writeGlb(), 1, 4, 0, 9, 0f)
        assertEquals(TeamColor.RED.main, build(model, Pose.REST, TeamColor.RED).mats[0].rgb)
        assertEquals(TeamColor.BLUE.main, build(model, Pose.REST, TeamColor.BLUE).mats[0].rgb)
    }

    @Test
    fun builtInModelsRenderPixels() {
        val cam = Camera(com.clashclaude.game.game.View.PITCH_DEG, 50f)
        for (m in Models.sprites) {
            val soup = Sculpt().also { m.build(it, Pose.REST, TeamColor.BLUE) }.soup
            val px = render(soup, placeInWorld(1.57f), cam, 200, 260, 100f, 200f)
            assertTrue("${m.id} draws something", px.count { it ushr 24 != 0 } > 200)
        }
    }
}
