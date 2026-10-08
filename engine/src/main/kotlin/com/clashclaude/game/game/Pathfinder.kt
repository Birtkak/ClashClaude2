package com.clashclaude.game.game

import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * A* over a half-tile grid for ground troops. Cells are blocked by the river (except the
 * bridges, unless the unit jumps it), the arena edge, and towers/buildings inflated by
 * the walker's radius. Other troops are not obstacles; collisions sort those out.
 */
class Pathfinder(private val entities: List<Combatant>) {

    /**
     * Returns waypoints (after the start) leading toward (gx, gy), or null if unreachable.
     * [goal] is excluded from the obstacles so units can path right up to what they attack.
     */
    fun findPath(c: Combatant, gx: Float, gy: Float, goal: Combatant?): List<Pair<Float, Float>>? {
        val obstacles = entities.filter { it.kind != Kind.TROOP && it.alive && it !== goal }
        val blocked = BooleanArray(COLS * ROWS) { i -> isBlocked(c, obstacles, cx(i % COLS), cy(i / COLS)) }

        // If a shove left us overlapping something, plan from the closest free cell.
        var start = cellOf(c.x, c.y)
        if (blocked[start]) start = nearestOpen(blocked, start) ?: return null
        var goalCell = cellOf(gx, gy)
        if (blocked[goalCell]) goalCell = nearestOpen(blocked, goalCell) ?: return null
        if (start == goalCell) return listOf(gx to gy)

        val g = FloatArray(COLS * ROWS) { Float.MAX_VALUE }
        val from = IntArray(COLS * ROWS) { -1 }
        val closed = BooleanArray(COLS * ROWS)
        val open = PriorityQueue<Node>(64, compareBy { it.f })
        g[start] = 0f
        open += Node(start, heuristic(start, goalCell))

        while (open.isNotEmpty()) {
            val cur = open.poll()!!.cell
            if (closed[cur]) continue
            if (cur == goalCell) return smooth(c, obstacles, rebuild(from, cur), gx, gy)
            closed[cur] = true
            val col = cur % COLS
            val row = cur / COLS
            for (d in DIRS) {
                val nc = col + d[0]
                val nr = row + d[1]
                if (nc !in 0 until COLS || nr !in 0 until ROWS) continue
                val next = nr * COLS + nc
                if (closed[next] || blocked[next]) continue
                // No cutting corners diagonally past a blocked cell.
                if (d[0] != 0 && d[1] != 0 && (blocked[row * COLS + nc] || blocked[nr * COLS + col])) continue
                val step = if (d[0] != 0 && d[1] != 0) DIAG else 1f
                val ng = g[cur] + step
                if (ng < g[next]) {
                    g[next] = ng
                    from[next] = cur
                    open += Node(next, ng + heuristic(next, goalCell))
                }
            }
        }
        return null
    }

    private class Node(val cell: Int, val f: Float)

    private fun isBlocked(c: Combatant, obstacles: List<Combatant>, x: Float, y: Float): Boolean {
        if (x < c.radius * 0.5f || x > Arena.WIDTH - c.radius * 0.5f) return true
        if (!c.jumpsRiver && Arena.inRiver(y) && !Arena.BRIDGES.any { abs(x - it) <= Arena.BRIDGE_HALF_WIDTH - 0.2f }) {
            return true
        }
        return obstacles.any { hypot(it.x - x, it.y - y) < it.radius + c.radius * 0.8f }
    }

    private fun nearestOpen(blocked: BooleanArray, cell: Int): Int? {
        val col = cell % COLS
        val row = cell / COLS
        for (r in 1..8) {
            var best: Int? = null
            var bestD = Float.MAX_VALUE
            for (dr in -r..r) for (dc in -r..r) {
                if (max(abs(dr), abs(dc)) != r) continue
                val nc = col + dc
                val nr = row + dr
                if (nc !in 0 until COLS || nr !in 0 until ROWS) continue
                val i = nr * COLS + nc
                val d = sqrt((dr * dr + dc * dc).toFloat())
                if (!blocked[i] && d < bestD) {
                    best = i; bestD = d
                }
            }
            if (best != null) return best
        }
        return null
    }

    private fun rebuild(from: IntArray, end: Int): List<Int> {
        val cells = ArrayList<Int>()
        var cur = end
        while (cur != -1) {
            cells += cur
            cur = from[cur]
        }
        cells.reverse()
        return cells
    }

    /** String-pulls the cell path into a few straight segments, ending exactly at the goal. */
    private fun smooth(
        c: Combatant, obstacles: List<Combatant>, cells: List<Int>, gx: Float, gy: Float,
    ): List<Pair<Float, Float>> {
        val points = cells.map { cx(it % COLS) to cy(it / COLS) }.toMutableList()
        points[points.lastIndex] = gx to gy
        val out = ArrayList<Pair<Float, Float>>()
        var anchor = c.x to c.y
        var i = 0
        while (i < points.size) {
            var far = i
            for (j in points.lastIndex downTo i + 1) {
                if (clearLine(c, obstacles, anchor, points[j])) {
                    far = j
                    break
                }
            }
            out += points[far]
            anchor = points[far]
            i = far + 1
        }
        return out
    }

    private fun clearLine(
        c: Combatant, obstacles: List<Combatant>, a: Pair<Float, Float>, b: Pair<Float, Float>,
    ): Boolean {
        val len = hypot(b.first - a.first, b.second - a.second)
        val steps = max(1, ceil(len / 0.25f).toInt())
        for (s in 1 until steps) {
            val t = s / steps.toFloat()
            val x = a.first + (b.first - a.first) * t
            val y = a.second + (b.second - a.second) * t
            if (isBlocked(c, obstacles, x, y)) return false
        }
        return true
    }

    companion object {
        const val CELL = 0.5f
        val COLS = (Arena.WIDTH / CELL).toInt()
        val ROWS = (Arena.HEIGHT / CELL).toInt()
        private const val DIAG = 1.4142135f
        private val DIRS = arrayOf(
            intArrayOf(1, 0), intArrayOf(-1, 0), intArrayOf(0, 1), intArrayOf(0, -1),
            intArrayOf(1, 1), intArrayOf(1, -1), intArrayOf(-1, 1), intArrayOf(-1, -1),
        )

        private fun cx(col: Int) = (col + 0.5f) * CELL
        private fun cy(row: Int) = (row + 0.5f) * CELL

        fun cellOf(x: Float, y: Float): Int {
            val col = min(COLS - 1, max(0, (x / CELL).toInt()))
            val row = min(ROWS - 1, max(0, (y / CELL).toInt()))
            return row * COLS + col
        }

        private fun heuristic(a: Int, b: Int): Float {
            val dx = abs(a % COLS - b % COLS).toFloat()
            val dy = abs(a / COLS - b / COLS).toFloat()
            return max(dx, dy) + (DIAG - 1f) * min(dx, dy)
        }
    }
}
