package app.mantra.cards.data

import java.text.Normalizer

/** A ready-made deck shipped with the app: a text file in assets/decks with lines "word — перевод". */
data class ReadyDeck(val file: String, val name: String, val emoji: String, val color: Long, val about: String, val newPerDay: Int)

val READY_DECKS = listOf(
    ReadyDeck("en_common", "Английский: частые слова", "🇬🇧", 0xFF7CC4FF, "Самые нужные слова на каждый день", 10),
    ReadyDeck("en_irregular", "Неправильные глаголы", "📚", 0xFFFFC266, "Три формы глагола и перевод", 5),
    ReadyDeck("en_travel", "Фразы для путешествий", "✈️", 0xFF6EE7A8, "Вокзал, отель, кафе, магазин, аптека", 5),
)

/** How a typed answer compares with the right one. */
enum class Typed { RIGHT, ALMOST, WRONG }

/**
 * Checks a typed word against the card's front. Case, punctuation, "to " before verbs and ё/е do not matter;
 * one wrong letter in a longer word counts as almost right. For "go · went · gone" the first form is enough.
 */
fun checkTyped(typed: String, answer: String): Typed {
    fun norm(s: String) = Normalizer.normalize(s.lowercase().replace('ё', 'е'), Normalizer.Form.NFC)
        .replace(Regex("[^\\p{L}\\p{N}' ]"), " ").replace(Regex("\\s+"), " ").trim().removePrefix("to ").trim()
    val t = norm(typed)
    if (t.isEmpty()) return Typed.WRONG
    val variants = (listOf(answer) + answer.split('·', '/', ',', ';')).map(::norm).filter { it.isNotEmpty() }.distinct()
    if (variants.any { it == t }) return Typed.RIGHT
    if (variants.any { it.length >= 5 && distance(it, t) <= 1 }) return Typed.ALMOST
    return Typed.WRONG
}

private fun distance(a: String, b: String): Int {
    var prev = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        val cur = IntArray(b.length + 1); cur[0] = i
        for (j in 1..b.length) cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
        prev = cur
    }
    return prev[b.length]
}

/** Cleans text shared from another app into a card front: no links, no quotes around, at most a few hundred letters. */
fun cleanShared(text: String): String =
    text.replace(Regex("https?://\\S+"), " ").replace(Regex("\\s+"), " ").trim().trim('"', '«', '»', '“', '”', '\'', '.', ',', ';', ':').trim().take(300)
