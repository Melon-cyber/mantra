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
import app.mantra.cards.data.Lookup
import app.mantra.cards.data.STARTER_WORDS
import app.mantra.cards.data.Translator
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
    fun addPairs(deckId: String, pairs: List<Pair<String, String>>): Int = store.addCards(deckId, pairs).also { note(Note("Добавлено карточек: $it")) }

    /** Translates [text] for a card of [deck]: from its language to Russian, or back when [toForeign]. */
    fun translate(text: String, deck: Deck, toForeign: Boolean = false, done: (String?) -> Unit) {
        viewModelScope.launch {
            val lang = deck.lang.code
            // Into Russian, the card gets the main translation plus a couple of other common meanings.
            val result = runCatching {
                if (toForeign) Translator.translate(text, "ru", lang)
                else Translator.lookup(text, lang, if (lang == "ru") "en" else "ru", examples = false).also { remember(lang, text, it) }.answer()
            }.getOrNull()
            if (result == null) note(Note("Перевод не получился. Проверь интернет или впиши вручную."))
            done(result?.takeIf { it.isNotBlank() })
        }
    }

    fun addText(deckId: String, text: String): Int = store.addCards(deckId, parseCards(text)).also { note(Note("Добавлено карточек: $it")) }

    fun starterDeck(): Deck {
        val deck = store.saveDeck(Deck("", "Английский: первые слова", "🇬🇧", 0xFF7CC4FF, newPerDay = 10))
        store.addCards(deck.id, parseCards(STARTER_WORDS))
        return deck
    }

    fun study(deckId: String?) { mSession.value = Session(deckId, store.next(deckId)) }
    fun reveal() {
        mSession.value = mSession.value?.copy(shown = true)
        mSession.value?.card?.let { card -> state.value.decks.firstOrNull { it.id == card.deckId }?.let { context(card, it) } }
    }

    // ---- Context: meanings and example sentences, shown after the answer is revealed ----
    private val lookupFile = java.io.File(application.filesDir, "lookups.json")
    private val gson = com.google.gson.Gson()
    private val mLookups = MutableStateFlow<Map<String, Lookup>>(runCatching {
        gson.fromJson<Map<String, Lookup>>(lookupFile.readText(), object : com.google.gson.reflect.TypeToken<Map<String, Lookup>>() {}.type)
    }.getOrNull().orEmpty().filterValues { it?.translation != null })
    /** Saved lookups by "language|word", so context also shows without internet next time. */
    val lookups = mLookups.asStateFlow()
    private val mLookupFailed = MutableStateFlow<Set<String>>(emptySet())
    val lookupFailed = mLookupFailed.asStateFlow()
    fun lookupKey(card: Card, deck: Deck) = deck.lang.code + "|" + card.front.trim().lowercase()

    private fun remember(lang: String, word: String, l: Lookup) {
        val key = lang + "|" + word.trim().lowercase()
        val old = mLookups.value[key]
        if (old != null && old.examples.isNotEmpty() && l.examples.isEmpty()) return
        mLookups.value = (mLookups.value + (key to l)).entries.toList().takeLast(3000).associate { it.key to it.value }
        val snapshot = mLookups.value
        viewModelScope.launch(Dispatchers.IO) { runCatching { lookupFile.writeText(gson.toJson(snapshot)) } }
    }

    /** Loads meanings and examples for the card's word, once; Russian-language decks have nothing to look up. */
    fun context(card: Card, deck: Deck) {
        val key = lookupKey(card, deck)
        if (deck.lang.code == "ru" || card.front.length > 80) return
        if (mLookups.value[key]?.examples?.isNotEmpty() == true || mLookups.value[key]?.meanings?.isNotEmpty() == true) return
        mLookupFailed.value = mLookupFailed.value - key
        viewModelScope.launch {
            runCatching { Translator.lookup(card.front.trim(), deck.lang.code, "ru") }
                .onSuccess { remember(deck.lang.code, card.front, it) }
                .onFailure { mLookupFailed.value = mLookupFailed.value + key }
        }
    }
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

    fun say(text: String, deck: Deck? = null) = app.speaker.say(text, deck?.lang?.speech)

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
