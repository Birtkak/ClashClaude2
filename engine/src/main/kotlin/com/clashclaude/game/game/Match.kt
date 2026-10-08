package com.clashclaude.game.game

/**
 * What the battle screen talks to: a running [Battle] seen from one [viewer] team.
 *
 * The screen never changes the battle directly. It sends [Command]s and calls [advance]
 * once per frame. [LocalMatch] runs the simulation on the phone; a future network match
 * would forward commands to the server and replace [battle] with the server's state.
 */
interface Match {
    val battle: Battle

    /** The team this device controls and draws at the bottom of the screen. */
    val viewer: Team

    fun send(command: Command)

    /**
     * Moves time forward by [frameSeconds] of real time. Returns how far (0..1) the screen
     * is between the last simulated tick and the next one, for smooth drawing.
     */
    fun advance(frameSeconds: Float): Float
}

/** A match simulated on this device, e.g. against the AI. */
class LocalMatch(override val battle: Battle, override val viewer: Team = Team.PLAYER) : Match {
    private var pending = 0f

    override fun send(command: Command) {
        if (command.team == viewer) battle.submit(command)
    }

    override fun advance(frameSeconds: Float): Float {
        pending += frameSeconds
        var steps = 0
        while (pending >= Battle.TICK_SECONDS && steps < MAX_STEPS_PER_FRAME) {
            battle.step()
            pending -= Battle.TICK_SECONDS
            steps++
        }
        // After a long stall, drop the backlog instead of fast-forwarding.
        if (pending >= Battle.TICK_SECONDS) pending = 0f
        return (pending / Battle.TICK_SECONDS).coerceIn(0f, 1f)
    }

    private companion object {
        const val MAX_STEPS_PER_FRAME = 4
    }
}
