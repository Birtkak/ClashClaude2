package com.clashclaude.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.clashclaude.game.audio.SoundBoard
import com.clashclaude.game.data.CardDef
import com.clashclaude.game.data.DeckRepository
import com.clashclaude.game.game.Outcome
import com.clashclaude.game.ui.BattleScreen
import com.clashclaude.game.ui.ClashTheme
import com.clashclaude.game.ui.HomeScreen
import com.clashclaude.game.ui.Sprites

class MainActivity : ComponentActivity() {
    private lateinit var sounds: SoundBoard

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repo = DeckRepository(applicationContext)
        // Baked sprite sheets and card art live in assets/sprites (see tools/baker).
        Sprites.loader = { name ->
            assets.open("sprites/$name.png").use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
        }
        sounds = SoundBoard(applicationContext).apply {
            soundOn = repo.soundOn
            musicOn = repo.musicOn
        }
        setContent {
            ClashTheme(displayFont = FontFamily(Font(R.font.lilita_one))) {
                // null = home screen, otherwise the deck being played in battle.
                var battleDeck by remember { mutableStateOf<List<CardDef>?>(null) }
                val deck = battleDeck
                if (deck == null) {
                    HomeScreen(
                        repo,
                        audio = sounds,
                        onAudioSettings = { sound, music ->
                            sounds.soundOn = sound
                            sounds.musicOn = music
                        },
                        version = packageManager.getPackageInfo(packageName, 0).versionName ?: "?",
                        onBattle = { battleDeck = it },
                    )
                } else {
                    BattleScreen(deck, audio = sounds) { outcome ->
                        when (outcome) {
                            Outcome.WIN -> repo.wins++
                            Outcome.LOSS -> repo.losses++
                            Outcome.DRAW -> Unit
                        }
                        battleDeck = null
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        sounds.setPaused(true)
    }

    override fun onResume() {
        super.onResume()
        sounds.setPaused(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        sounds.release()
    }
}
