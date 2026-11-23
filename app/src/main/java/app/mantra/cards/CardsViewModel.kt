package app.mantra.cards

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.mantra.cards.data.Card
import app.mantra.cards.data.CardState
import app.mantra.cards.data.Deck
import app.mantra.cards.data.DeckCounts
import app.mantra.cards.data.Grade
import app.mantra.cards.data.STARTER_WORDS
import app.mantra.cards.data.Saved
import app.mantra.cards.data.parseCards
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Note(val text: String, val undo: (() -> Unit)? = null)

/** A study session: which decks, the card on screen and how many were answered. */
data class Session(val deckId: String?, val card: Card?, val answered: Int = 0, val shown: Boolean = false, val last: Pair<Card, Boolean>? = null)

class CardsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MantraApp
    private val store = app.store
    val state: StateFlow<Saved> = store.state
    private val mNote = MutableStateFlow<Note?>(null)
    val note = mNote.asStateFlow()
    private val mSession = MutableStateFlow<Session?>(null)
    val session = mSession.asStateFlow()

    fun note(value: Note?) { mNote.value = value }
    fun counts(deck: Deck): DeckCounts = store.counts(deck)

    fun saveDeck(d: Deck) = store.saveDeck(d)
    fun deleteDeck(d: Deck) { store.deleteDeck(d.id); note(Note("Колода «${d.name}» удалена")) }
    fun saveCard(c: Card) = store.saveCard(c)
    fun deleteCard(c: Card) { store.deleteCard(c.id); note(Note("Карточка удалена") { store.saveCard(c) }) }
    fun addText(deckId: String, text: String): Int = store.addCards(deckId, parseCards(text)).also { note(Note("Добавлено карточек: $it")) }

    fun starterDeck(): Deck {
        val deck = store.saveDeck(Deck("", "Английский: первые слова", "🇬🇧", 0xFF7CC4FF, newPerDay = 10))
        store.addCards(deck.id, parseCards(STARTER_WORDS))
        return deck
    }

    fun study(deckId: String?) { mSession.value = Session(deckId, store.next(deckId)) }
    fun reveal() { mSession.value = mSession.value?.copy(shown = true) }
    fun endStudy() { mSession.value = null }

    fun answer(grade: Grade) {
        val s = mSession.value ?: return
        val card = s.card ?: return
        store.answer(card, grade)
        mSession.value = s.copy(card = store.next(s.deckId, skip = card.id), answered = s.answered + 1, shown = false, last = card to (card.state == CardState.NEW))
    }

    fun undoAnswer() {
        val s = mSession.value ?: return
        val (card, wasNew) = s.last ?: return
        store.undo(card, wasNew)
        mSession.value = s.copy(card = card, answered = (s.answered - 1).coerceAtLeast(0), shown = true, last = null)
    }

    /** Keeps the card on screen in step with an edit made during study. */
    fun editedDuringStudy(card: Card) { mSession.value = mSession.value?.let { if (it.card?.id == card.id) it.copy(card = card) else it } }

    fun say(text: String) = app.speaker.say(text)

    fun backup(uri: Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { app.contentResolver.openOutputStream(uri)?.use { it.write(store.exportJson().toByteArray()) } != null }.getOrDefault(false) }
            note(Note(if (ok) "Копия сохранена" else "Не удалось сохранить файл"))
        }
    }

    fun restore(uri: Uri) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) { runCatching { app.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull() }
            val added = text?.let { runCatching { store.importJson(it) }.getOrNull() }
            note(Note(if (added == null) "Это не копия Мантры" else "Восстановлено. Новых карточек: $added"))
        }
    }

    /** Reads a text or table file for importing into a deck. */
    suspend fun readText(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching { app.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
    }
}
