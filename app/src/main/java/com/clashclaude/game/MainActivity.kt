package com.clashclaude.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.clashclaude.game.data.CardDef
import com.clashclaude.game.data.DeckRepository
import com.clashclaude.game.game.Outcome
import com.clashclaude.game.ui.BattleScreen
import com.clashclaude.game.ui.ClashTheme
import com.clashclaude.game.ui.HomeScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repo = DeckRepository(applicationContext)
        setContent {
            ClashTheme {
                // null = home screen, otherwise the deck being played in battle.
                var battleDeck by remember { mutableStateOf<List<CardDef>?>(null) }
                val deck = battleDeck
                if (deck == null) {
                    HomeScreen(repo, onBattle = { battleDeck = it })
                } else {
                    BattleScreen(deck) { outcome ->
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
}
