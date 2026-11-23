package app.mantra.cards.data

import android.content.Context
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.time.LocalDate
import java.util.UUID

data class Saved(
    val version: Int = 1,
    val decks: List<Deck> = emptyList(),
    val cards: List<Card> = emptyList(),
    /** Day ("2026-11-23") → what was done. */
    val log: Map<String, DayLog> = emptyMap(),
    /** Cards started for the first time, per day and deck: "2026-11-23|deckId" → count. */
    val started: Map<String, Int> = emptyMap(),
)

data class DeckCounts(val due: Int, val fresh: Int, val learning: Int, val total: Int)

/** Decks, cards and the study log in one JSON file in the app's private folder. */
class Store(context: Context) {
    private val file = File(context.filesDir, "mantra.json")
    private val gson = GsonBuilder().create()
    private val lock = Any()
    private val mState = MutableStateFlow(load())
    val state: StateFlow<Saved> = mState.asStateFlow()

    fun saveDeck(d: Deck): Deck {
        val clean = d.copy(id = d.id.ifBlank { UUID.randomUUID().toString() }, name = d.name.trim().ifBlank { "Колода" }.take(60), newPerDay = d.newPerDay.coerceIn(0, 200))
        edit { s -> s.copy(decks = s.decks.filter { it.id != clean.id } + clean) }
        return clean
    }

    fun deleteDeck(id: String) = edit { s -> s.copy(decks = s.decks.filter { it.id != id }, cards = s.cards.filter { it.deckId != id }) }

    fun saveCard(c: Card): Card {
        val clean = c.copy(id = c.id.ifBlank { UUID.randomUUID().toString() }, front = c.front.trim(), back = c.back.trim(), note = c.note.trim())
        edit { s -> s.copy(cards = s.cards.filter { it.id != clean.id } + clean) }
        return clean
    }

    fun deleteCard(id: String) = edit { s -> s.copy(cards = s.cards.filter { it.id != id }) }

    /** Adds many cards at once; lines whose front is already in the deck are skipped. Returns how many were added. */
    fun addCards(deckId: String, pairs: List<Pair<String, String>>): Int {
        var added = 0
        edit { s ->
            val known = s.cards.filter { it.deckId == deckId }.map { it.front.lowercase() }.toMutableSet()
            val now = System.currentTimeMillis()
            val fresh = pairs.filter { known.add(it.first.lowercase()) }.mapIndexed { i, (f, b) -> Card(UUID.randomUUID().toString(), deckId, f.take(500), b.take(1000), created = now + i) }
            added = fresh.size
            s.copy(cards = s.cards + fresh)
        }
        return added
    }

    fun answer(card: Card, grade: Grade) {
        val today = LocalDate.now().toString()
        edit { s ->
            val updated = Scheduler.answer(card, grade)
            val log = s.log[today] ?: DayLog()
            val key = "$today|${card.deckId}"
            s.copy(cards = s.cards.map { if (it.id == card.id) updated else it },
                log = s.log + (today to log.copy(reviews = log.reviews + 1, started = log.started + if (card.state == CardState.NEW) 1 else 0)),
                started = if (card.state == CardState.NEW) s.started.filterKeys { it.substringBefore('|') >= LocalDate.now().minusDays(3).toString() } + (key to (s.started[key] ?: 0) + 1) else s.started)
        }
    }

    /** Puts a card back as it was before the last answer. */
    fun undo(previous: Card, wasNew: Boolean) {
        val today = LocalDate.now().toString()
        edit { s ->
            val log = s.log[today] ?: DayLog()
            val key = "$today|${previous.deckId}"
            s.copy(cards = s.cards.map { if (it.id == previous.id) previous else it },
                log = s.log + (today to log.copy(reviews = (log.reviews - 1).coerceAtLeast(0), started = (log.started - if (wasNew) 1 else 0).coerceAtLeast(0))),
                started = if (wasNew) s.started + (key to ((s.started[key] ?: 1) - 1).coerceAtLeast(0)) else s.started)
        }
    }

    fun counts(deck: Deck, now: Long = System.currentTimeMillis()): DeckCounts {
        val s = mState.value
        val cards = s.cards.filter { it.deckId == deck.id }
        val endOfDay = Scheduler.startOfDay(LocalDate.now().plusDays(1))
        val startedToday = s.started["${LocalDate.now()}|${deck.id}"] ?: 0
        val fresh = minOf(cards.count { it.state == CardState.NEW }, (deck.newPerDay - startedToday).coerceAtLeast(0))
        return DeckCounts(
            due = cards.count { it.state == CardState.REVIEW && it.due < endOfDay },
            fresh = fresh,
            learning = cards.count { (it.state == CardState.LEARNING || it.state == CardState.RELEARNING) && it.due <= now + 20 * 60_000 },
            total = cards.size,
        )
    }

    /**
     * The next card to study: cards being learned that are due now, then today's reviews, then new cards
     * (within the deck's daily limit). With nothing else left, a card being learned comes a little early.
     */
    fun next(deckId: String?, skip: String? = null, now: Long = System.currentTimeMillis()): Card? {
        val s = mState.value
        val decks = s.decks.filter { deckId == null || it.id == deckId }.associateBy { it.id }
        val cards = s.cards.filter { it.deckId in decks && it.id != skip }
        val endOfDay = Scheduler.startOfDay(LocalDate.now().plusDays(1))
        val learning = cards.filter { it.state == CardState.LEARNING || it.state == CardState.RELEARNING }.sortedBy { it.due }
        learning.firstOrNull { it.due <= now }?.let { return it }
        cards.filter { it.state == CardState.REVIEW && it.due < endOfDay }.minByOrNull { it.due }?.let { return it }
        for (deck in decks.values.sortedBy { it.created }) {
            val left = deck.newPerDay - (s.started["${LocalDate.now()}|${deck.id}"] ?: 0)
            if (left > 0) cards.filter { it.deckId == deck.id && it.state == CardState.NEW }.minByOrNull { it.created }?.let { return it }
        }
        return learning.firstOrNull { it.due <= now + 20 * 60_000 } ?: if (skip != null) s.cards.firstOrNull { it.id == skip && (it.state == CardState.LEARNING || it.state == CardState.RELEARNING) && it.due <= now + 20 * 60_000 } else null
    }

    fun exportJson(): String = GsonBuilder().setPrettyPrinting().create().toJson(mState.value)

    /** Adds decks and cards from a backup; ones already here stay as they are. Returns how many cards were added. */
    fun importJson(text: String): Int {
        val other = repair(gson.fromJson(JsonParser.parseString(text), Saved::class.java) ?: return 0)
        var added = 0
        edit { s ->
            val decks = s.decks.map { it.id }.toSet()
            val cards = s.cards.map { it.id }.toSet()
            val newCards = other.cards.filter { it.id !in cards }.also { added = it.size }
            s.copy(decks = s.decks + other.decks.filter { it.id !in decks }, cards = s.cards + newCards)
        }
        return added
    }

    private fun edit(change: (Saved) -> Saved) = synchronized(lock) {
        val next = change(mState.value)
        if (next != mState.value) { mState.value = next; write(next) }
    }

    private fun load(): Saved = runCatching { file.takeIf { it.isFile }?.readText()?.let { gson.fromJson(it, Saved::class.java) } }.getOrNull()?.let(::repair) ?: Saved()

    @Suppress("SENSELESS_COMPARISON", "UNNECESSARY_SAFE_CALL", "USELESS_ELVIS")
    private fun repair(s: Saved): Saved = Saved(
        decks = (s.decks ?: emptyList()).filter { it?.id != null }.map { it.copy(name = it.name ?: "Колода", emoji = it.emoji ?: "🧠", language = it.language ?: "EN") },
        cards = (s.cards ?: emptyList()).filter { it?.id != null && it.deckId != null }.map { it.copy(front = it.front ?: "", back = it.back ?: "", note = it.note ?: "", state = it.state ?: CardState.NEW) },
        log = s.log ?: emptyMap(),
        started = s.started ?: emptyMap(),
    )

    private fun write(s: Saved) {
        val temp = File(file.parentFile, "mantra.json.tmp")
        runCatching {
            temp.writeText(gson.toJson(s))
            if (!temp.renameTo(file)) { file.delete(); temp.renameTo(file) }
        }
    }
}
