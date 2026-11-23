package app.mantra.cards.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

data class Deck(
    val id: String,
    val name: String,
    val emoji: String = "🧠",
    val color: Long = 0xFFFFA8C5,
    /** How many new cards to start a day. */
    val newPerDay: Int = 15,
    val created: Long = System.currentTimeMillis(),
    /** Language being learned, for voice input and translation (a [Language] name). */
    val language: String = "EN",
    /** "TRANSLATION" — the answer is a Russian translation; "DEFINITION" — an explanation in the language itself. */
    val answer: String = "TRANSLATION",
) {
    val lang: Language get() = runCatching { Language.valueOf(language) }.getOrDefault(Language.EN)
    val definitions: Boolean get() = answer == "DEFINITION" && lang != Language.RU
}

enum class CardState { NEW, LEARNING, REVIEW, RELEARNING }

data class Card(
    val id: String,
    val deckId: String,
    val front: String,
    val back: String,
    val note: String = "",
    val created: Long = System.currentTimeMillis(),
    val state: CardState = CardState.NEW,
    val step: Int = 0,
    val ease: Double = 2.5,
    /** Days until the next review once learned. */
    val interval: Int = 0,
    val due: Long = 0,
    val reps: Int = 0,
    val lapses: Int = 0,
)

enum class Grade(val label: String) { AGAIN("Снова"), HARD("Трудно"), GOOD("Хорошо"), EASY("Легко") }

/** What happened on one day: reviews done and new cards started. */
data class DayLog(val reviews: Int = 0, val started: Int = 0)

/**
 * Spaced repetition in the spirit of Anki's classic SM-2: a new card is shown again after a minute and ten minutes,
 * then after a day, and every good answer stretches the pause. A forgotten card starts over, a little sooner each time.
 */
object Scheduler {
    private val learningSteps = listOf(60_000L, 600_000L)
    private const val RELEARN_STEP = 600_000L

    fun startOfDay(day: LocalDate): Long = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    fun dayOf(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

    fun answer(card: Card, grade: Grade, now: Long = System.currentTimeMillis(), random: Random = Random.Default): Card {
        val today = dayOf(now)
        fun inDays(days: Int) = startOfDay(today.plusDays(days.toLong()))
        // A little randomness keeps cards added together from always coming back on the same day.
        fun fuzz(days: Int) = if (days <= 2) days else (days * (0.95 + random.nextDouble() * 0.1)).roundToInt().coerceAtLeast(3)
        val counted = card.copy(reps = card.reps + 1)
        return when (card.state) {
            CardState.NEW, CardState.LEARNING -> when (grade) {
                Grade.AGAIN -> counted.copy(state = CardState.LEARNING, step = 0, due = now + learningSteps[0])
                Grade.HARD -> counted.copy(state = CardState.LEARNING, due = now + if (card.step == 0) 360_000L else learningSteps[card.step.coerceAtMost(learningSteps.lastIndex)])
                Grade.GOOD -> {
                    val nextStep = if (card.state == CardState.NEW) 1 else card.step + 1
                    if (nextStep < learningSteps.size) counted.copy(state = CardState.LEARNING, step = nextStep, due = now + learningSteps[nextStep])
                    else graduate(counted, 1, inDays(1))
                }
                Grade.EASY -> graduate(counted, 4, inDays(4))
            }
            CardState.REVIEW -> when (grade) {
                Grade.AGAIN -> counted.copy(state = CardState.RELEARNING, step = 0, lapses = card.lapses + 1, ease = (card.ease - 0.2).coerceAtLeast(1.3),
                    interval = max(1, (card.interval * 0.5).roundToInt()), due = now + RELEARN_STEP)
                Grade.HARD -> { val d = fuzz(max(card.interval + 1, (card.interval * 1.2).roundToInt())); counted.copy(ease = (card.ease - 0.15).coerceAtLeast(1.3), interval = d, due = inDays(d)) }
                Grade.GOOD -> { val d = fuzz(max(card.interval + 1, (card.interval * card.ease).roundToInt())); counted.copy(interval = d, due = inDays(d)) }
                Grade.EASY -> { val d = fuzz(max(card.interval + 2, (card.interval * card.ease * 1.3).roundToInt())); counted.copy(ease = card.ease + 0.15, interval = d, due = inDays(d)) }
            }
            CardState.RELEARNING -> when (grade) {
                Grade.AGAIN, Grade.HARD -> counted.copy(due = now + RELEARN_STEP)
                Grade.GOOD -> counted.copy(state = CardState.REVIEW, due = inDays(card.interval.coerceAtLeast(1)))
                Grade.EASY -> { val d = card.interval.coerceAtLeast(1) + 1; counted.copy(state = CardState.REVIEW, interval = d, due = inDays(d)) }
            }
        }
    }

    private fun graduate(card: Card, days: Int, due: Long) = card.copy(state = CardState.REVIEW, step = 0, interval = days, due = due)

    /** "1 мин", "10 мин", "3 дн.", "2 мес." — when the card returns after this answer. */
    fun preview(card: Card, grade: Grade, now: Long = System.currentTimeMillis()): String {
        val next = answer(card, grade, now, Random(1))
        val ms = next.due - now
        if (next.state == CardState.LEARNING || next.state == CardState.RELEARNING) return "${(ms / 60_000).coerceAtLeast(1)} мин"
        val days = next.interval
        return when {
            days < 30 -> "$days дн."
            days < 365 -> "${(days / 30.0).let { if (it < 10) "%.1f".format(java.util.Locale.forLanguageTag("ru"), it).removeSuffix(",0") else it.roundToInt().toString() }} мес."
            else -> "${"%.1f".format(java.util.Locale.forLanguageTag("ru"), days / 365.0).removeSuffix(",0")} г."
        }
    }
}

/** Lines like "cat — кошка" or "cat;кошка" become cards. Returns pairs of (front, back). */
fun parseCards(text: String): List<Pair<String, String>> {
    val separators = listOf("\t", " — ", " – ", " - ", ";", " = ", ":", ",")
    val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
    val sep = separators.maxByOrNull { s -> lines.count { it.contains(s) } }?.takeIf { s -> lines.count { it.contains(s) } > 0 } ?: return emptyList()
    return lines.mapNotNull { line ->
        val i = line.indexOf(sep)
        if (i <= 0) null else line.substring(0, i).trim().trim('"') to line.substring(i + sep.length).trim().trim('"')
    }.filter { it.first.isNotEmpty() && it.second.isNotEmpty() }
}

val EMOJIS = listOf("🧠", "🇬🇧", "🇩🇪", "🇫🇷", "🇪🇸", "🇨🇳", "🇯🇵", "🇹🇷", "📚", "🧪", "➗", "🌍", "🏛️", "💻", "⚖️", "🩺", "🎵", "🎨", "🚗", "✈️", "💬", "⭐")
val COLORS = listOf(0xFFFFA8C5, 0xFF7CC4FF, 0xFF6EE7A8, 0xFFFFC266, 0xFFB69CFF, 0xFFFF8A80, 0xFF5EEAD4, 0xFFD4F06A)

fun plural(n: Int, one: String, few: String, many: String): String {
    val m100 = n % 100; val m10 = n % 10
    return when { m100 in 11..14 -> many; m10 == 1 -> one; m10 in 2..4 -> few; else -> many }
}

/** A small deck to try the app with. */
val STARTER_WORDS = """
time — время
person — человек
year — год
way — путь, способ
day — день
thing — вещь
world — мир
life — жизнь
hand — рука
part — часть
child — ребёнок
eye — глаз
woman — женщина
place — место
work — работа
week — неделя
case — случай
point — точка, смысл
number — число
group — группа
problem — проблема
fact — факт
to be — быть
to have — иметь
to do — делать
to say — сказать
to get — получать
to make — делать, создавать
to go — идти, ехать
to know — знать
to take — брать
to see — видеть
to come — приходить
to think — думать
to look — смотреть
to want — хотеть
to give — давать
to use — использовать
to find — находить
to tell — рассказывать
good — хороший
new — новый
first — первый
last — последний
long — длинный, долгий
great — великий, отличный
little — маленький
own — собственный
other — другой
old — старый
""".trimIndent()
