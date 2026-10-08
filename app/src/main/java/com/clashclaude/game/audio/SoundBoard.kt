package com.clashclaude.game.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.SystemClock
import com.clashclaude.game.R
import com.clashclaude.game.game.Sfx
import com.clashclaude.game.ui.GameAudio
import kotlin.random.Random

/**
 * Plays the synthesized effects (res/raw/sfx_*.ogg, made by tools/make_sounds.py) through a
 * SoundPool, and the looping battle music through a MediaPlayer.
 */
class SoundBoard(private val context: Context) : GameAudio {
    var soundOn = true
    var musicOn = true
        set(value) {
            field = value
            applyMusic()
        }

    private val pool = SoundPool.Builder()
        .setMaxStreams(10)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val soundIds = IntArray(Sfx.entries.size)
    private val lastPlayed = LongArray(Sfx.entries.size)

    private var player: MediaPlayer? = null
    private var wantMusic = false
    private var fast = false
    private var paused = false

    init {
        for (sfx in Sfx.entries) soundIds[sfx.ordinal] = pool.load(context, resource(sfx), 1)
    }

    override fun play(sfx: Sfx) {
        if (!soundOn) return
        // Rate-limit each sound so a big fight doesn't turn into noise.
        val now = SystemClock.uptimeMillis()
        if (now - lastPlayed[sfx.ordinal] < minGapMs(sfx)) return
        lastPlayed[sfx.ordinal] = now
        val v = volume(sfx)
        // A little pitch variation keeps repeated hits from sounding robotic.
        val rate = 0.94f + Random.nextFloat() * 0.12f
        pool.play(soundIds[sfx.ordinal], v, v, priority(sfx), 0, rate)
    }

    override fun music(playing: Boolean, fast: Boolean) {
        wantMusic = playing
        this.fast = fast
        applyMusic()
    }

    /** Call from the activity's onPause/onResume so music stops in the background. */
    fun setPaused(paused: Boolean) {
        this.paused = paused
        applyMusic()
        if (paused) pool.autoPause() else pool.autoResume()
    }

    fun release() {
        pool.release()
        player?.release()
        player = null
    }

    private fun applyMusic() {
        val shouldPlay = wantMusic && musicOn && !paused
        if (!shouldPlay) {
            player?.let { if (it.isPlaying) it.pause() }
            if (!wantMusic) player?.seekTo(0)
            return
        }
        val p = player ?: MediaPlayer.create(context, R.raw.music_battle)?.apply {
            isLooping = true
            setVolume(0.45f, 0.45f)
        }?.also { player = it } ?: return
        val speed = if (fast) 1.15f else 1f
        if (p.playbackParams.speed != speed) p.playbackParams = p.playbackParams.setSpeed(speed)
        if (!p.isPlaying) p.start()
    }

    private fun resource(sfx: Sfx): Int = when (sfx) {
        Sfx.DEPLOY -> R.raw.sfx_deploy
        Sfx.LAND -> R.raw.sfx_land
        Sfx.LAND_HEAVY -> R.raw.sfx_land_heavy
        Sfx.SWORD -> R.raw.sfx_sword
        Sfx.PUNCH -> R.raw.sfx_punch
        Sfx.SPIN -> R.raw.sfx_spin
        Sfx.BOW -> R.raw.sfx_bow
        Sfx.HIT -> R.raw.sfx_hit
        Sfx.GUN -> R.raw.sfx_gun
        Sfx.CANNON -> R.raw.sfx_cannon
        Sfx.FIRE -> R.raw.sfx_fire
        Sfx.THROW -> R.raw.sfx_throw
        Sfx.BLIP -> R.raw.sfx_blip
        Sfx.ZAP -> R.raw.sfx_zap
        Sfx.EXPLOSION -> R.raw.sfx_explosion
        Sfx.BIG_EXPLOSION -> R.raw.sfx_big_explosion
        Sfx.VOLLEY -> R.raw.sfx_volley
        Sfx.DEATH -> R.raw.sfx_death
        Sfx.TOWER_DOWN -> R.raw.sfx_tower_down
        Sfx.CLICK -> R.raw.sfx_click
        Sfx.DENY -> R.raw.sfx_deny
        Sfx.VICTORY -> R.raw.sfx_victory
        Sfx.DEFEAT -> R.raw.sfx_defeat
    }

    private fun volume(sfx: Sfx): Float = when (sfx) {
        Sfx.HIT, Sfx.BLIP -> 0.35f
        Sfx.BOW, Sfx.SWORD, Sfx.PUNCH -> 0.45f
        Sfx.LAND, Sfx.DEATH, Sfx.SPIN, Sfx.THROW -> 0.55f
        Sfx.TOWER_DOWN, Sfx.BIG_EXPLOSION, Sfx.VICTORY, Sfx.DEFEAT -> 1f
        else -> 0.7f
    }

    private fun minGapMs(sfx: Sfx): Long = when (sfx) {
        Sfx.HIT, Sfx.BOW, Sfx.SWORD, Sfx.PUNCH -> 70
        Sfx.LAND, Sfx.DEATH -> 90
        else -> 50
    }

    private fun priority(sfx: Sfx): Int = when (sfx) {
        Sfx.TOWER_DOWN, Sfx.VICTORY, Sfx.DEFEAT, Sfx.BIG_EXPLOSION -> 3
        Sfx.DEPLOY, Sfx.DENY, Sfx.CLICK, Sfx.EXPLOSION, Sfx.CANNON -> 2
        else -> 1
    }
}
