package com.clashclaude.game.game

/**
 * A player action. Everything a person can do in a match is one of these, so a server can
 * receive them over the network, check them and feed them to [Battle.submit].
 */
sealed class Command {
    abstract val team: Team

    /** Play the card with this id from [team]'s hand at (x, y) in arena tiles. */
    data class PlayCard(override val team: Team, val cardId: String, val x: Float, val y: Float) : Command()

    /** Give up the match. */
    data class Surrender(override val team: Team) : Command()
}
