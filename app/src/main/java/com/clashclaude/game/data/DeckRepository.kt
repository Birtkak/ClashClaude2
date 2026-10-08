package com.clashclaude.game.data

import android.content.Context

/** Persists the player's decks and win/loss record in SharedPreferences. */
class DeckRepository(context: Context) {
    private val prefs = context.getSharedPreferences("clash_claude", Context.MODE_PRIVATE)

    fun loadDecks(): List<List<String>> = List(DECK_COUNT) { i ->
        val stored = prefs.getString("deck_$i", null)
        if (stored == null) {
            Cards.defaultDecks[i]
        } else {
            stored.split(",").filter { Cards.get(it) != null }.distinct().take(DECK_SIZE)
        }
    }

    fun saveDeck(index: Int, deck: List<String>) {
        prefs.edit().putString("deck_$index", deck.joinToString(",")).apply()
    }

    var selectedDeck: Int
        get() = prefs.getInt("selected_deck", 0).coerceIn(0, DECK_COUNT - 1)
        set(value) = prefs.edit().putInt("selected_deck", value).apply()

    var wins: Int
        get() = prefs.getInt("wins", 0)
        set(value) = prefs.edit().putInt("wins", value).apply()

    var losses: Int
        get() = prefs.getInt("losses", 0)
        set(value) = prefs.edit().putInt("losses", value).apply()

    var soundOn: Boolean
        get() = prefs.getBoolean("sound_on", true)
        set(value) = prefs.edit().putBoolean("sound_on", value).apply()

    var musicOn: Boolean
        get() = prefs.getBoolean("music_on", true)
        set(value) = prefs.edit().putBoolean("music_on", value).apply()

    companion object {
        const val DECK_COUNT = 3
        const val DECK_SIZE = 8
    }
}
