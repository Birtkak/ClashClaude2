package com.clashclaude.game.ui

import com.clashclaude.game.game.Sfx

/** What the screens need from the audio system; the Android implementation is SoundBoard. */
interface GameAudio {
    fun play(sfx: Sfx)

    /** Starts or stops the battle music; [fast] plays it quicker (double elixir). */
    fun music(playing: Boolean, fast: Boolean = false)

    companion object {
        val Silent = object : GameAudio {
            override fun play(sfx: Sfx) {}
            override fun music(playing: Boolean, fast: Boolean) {}
        }
    }
}
