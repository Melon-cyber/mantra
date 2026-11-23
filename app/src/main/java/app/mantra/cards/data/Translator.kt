package app.mantra.cards.data

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Languages a deck can be learned in: speech-recognition tag and translation code. */
enum class Language(val label: String, val speech: String, val code: String) {
    EN("Английский", "en-US", "en"),
    DE("Немецкий", "de-DE", "de"),
    FR("Французский", "fr-FR", "fr"),
    ES("Испанский", "es-ES", "es"),
    IT("Итальянский", "it-IT", "it"),
    TR("Турецкий", "tr-TR", "tr"),
    ZH("Китайский", "zh-CN", "zh-CN"),
    JA("Японский", "ja-JP", "ja"),
    KO("Корейский", "ko-KR", "ko"),
    RU("Русский", "ru-RU", "ru"),
}

/** Meanings of one part of speech: "глагол: брать, взять, принимать". */
data class Meaning(val part: String, val words: List<String>)

/** An explanation of the word in its own language, as in an English dictionary. */
data class Definition(val part: String, val text: String, val example: String = "")

/** An example sentence with the word, and its translation. */
data class Example(val text: String, val translation: String = "")

/** Everything found for a word: the main translation, meanings by part of speech and examples. */
data class Lookup(
    val translation: String,
    val meanings: List<Meaning> = emptyList(),
    val examples: List<Example> = emptyList(),
    val definitions: List<Definition> = emptyList(),
    /** True when looked up with definitions and examples (older saved lookups have only the translation). */
    val full: Boolean = false,
) {
    /** A card answer: the main translation plus a few other common meanings, or dictionary definitions. */
    fun answer(definitions: Boolean = false, max: Int = 3): String {
        if (definitions && this.definitions.isNotEmpty()) {
            val top = this.definitions.take(2)
            return if (top.size == 1) top[0].text else top.mapIndexed { i, d -> "${i + 1}) ${d.text}" }.joinToString("\n")
        }
        val words = (listOf(translation) + meanings.flatMap { it.words.take(2) }).map { it.trim() }.filter { it.isNotEmpty() }
        return words.distinctBy { it.lowercase() }.take(max).joinToString(", ")
    }

    /** A ready line for the card's example field: the sentence and, for translation cards, its translation. */
    fun exampleNote(definitions: Boolean): String {
        val e = examples.firstOrNull() ?: definitions.let { d -> this.definitions.firstOrNull { it.example.isNotBlank() }?.let { Example(it.example) } } ?: return ""
        return if (!definitions && e.translation.isNotBlank()) "${e.text} — ${e.translation}" else e.text
    }
}

private val PARTS = mapOf(
    "noun" to "сущ.", "verb" to "глагол", "adjective" to "прил.", "adverb" to "нареч.", "pronoun" to "местоим.",
    "preposition" to "предлог", "conjunction" to "союз", "interjection" to "межд.", "phrase" to "фраза", "abbreviation" to "сокр.",
)

/**
 * Translations for cards. The main source is the public Google Translate web endpoint (with dictionary meanings
 * and example sentences); when it does not answer, the free MyMemory service gives at least the translation.
 */
object Translator {
    private const val AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    suspend fun translate(text: String, from: String, to: String): String = lookup(text, from, to, examples = false).translation

    suspend fun lookup(text: String, from: String, to: String, examples: Boolean = true): Lookup = withContext(Dispatchers.IO) {
        val google = runCatching { google(text, from, to, examples) }.getOrNull()
        if (google != null && google.translation.isNotBlank()) return@withContext google
        val backup = runCatching { myMemory(text, from, to) }.getOrNull()
        if (!backup.isNullOrBlank()) return@withContext Lookup(backup)
        throw IOException("Перевод недоступен")
    }

    private fun get(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000; readTimeout = 15_000
            setRequestProperty("User-Agent", AGENT)
            setRequestProperty("Accept-Language", "ru,en;q=0.8")
        }
        try {
            if (connection.responseCode != 200) throw IOException("HTTP ${connection.responseCode}")
            return connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally { connection.disconnect() }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun google(text: String, from: String, to: String, examples: Boolean): Lookup {
        val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=$from&tl=$to&hl=en&dt=t&dt=bd&dt=md" + (if (examples) "&dt=ex" else "") + "&q=${enc(text)}"
        val root = JsonParser.parseString(get(url)).asJsonArray
        val translation = root.at(0)?.asArrayOrNull()?.joinToString("") { seg -> seg.asArrayOrNull()?.at(0)?.stringOrNull().orEmpty() }?.trim().orEmpty()
        val meanings = root.at(1)?.asArrayOrNull()?.mapNotNull { pos ->
            val arr = pos.asArrayOrNull() ?: return@mapNotNull null
            val part = arr.at(0)?.stringOrNull().orEmpty()
            val words = arr.at(1)?.asArrayOrNull()?.mapNotNull { it.stringOrNull() }.orEmpty()
            if (words.isEmpty()) null else Meaning(PARTS[part] ?: part, words.take(6))
        }.orEmpty()
        var found = root.at(13)?.asArrayOrNull()?.at(0)?.asArrayOrNull()?.mapNotNull { ex ->
            ex.asArrayOrNull()?.at(0)?.stringOrNull()?.replace(Regex("<[^>]+>"), "")?.trim()?.takeIf { it.isNotEmpty() }
        }.orEmpty().take(3).map { Example(it) }
        // Example sentences come in the word's language; translate them together in one request.
        if (found.isNotEmpty()) runCatching {
            val joined = found.joinToString("\n") { it.text }
            val tr = JsonParser.parseString(get("https://translate.googleapis.com/translate_a/single?client=gtx&sl=$from&tl=$to&dt=t&q=${enc(joined)}")).asJsonArray
            val lines = tr.at(0)?.asArrayOrNull()?.joinToString("") { seg -> seg.asArrayOrNull()?.at(0)?.stringOrNull().orEmpty() }.orEmpty().split("\n").map { it.trim() }
            if (lines.size == found.size) found = found.zip(lines) { e, t -> e.copy(translation = t) }
        }
        val definitions = root.at(12)?.asArrayOrNull()?.flatMap { pos ->
            val arr = pos.asArrayOrNull() ?: return@flatMap emptyList()
            val part = arr.at(0)?.stringOrNull().orEmpty()
            arr.at(1)?.asArrayOrNull()?.mapNotNull { d ->
                val a = d.asArrayOrNull() ?: return@mapNotNull null
                val t = a.at(0)?.stringOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                Definition(PARTS[part] ?: part, t, a.at(2)?.stringOrNull()?.trim().orEmpty())
            }.orEmpty().take(3)
        }.orEmpty().take(6)
        return Lookup(translation, meanings, found, definitions, full = examples)
    }

    private fun myMemory(text: String, from: String, to: String): String? {
        val pair = "${from.substringBefore('-')}|${to.substringBefore('-')}"
        val root = JsonParser.parseString(get("https://api.mymemory.translated.net/get?q=${enc(text)}&langpair=${enc(pair)}")).asJsonObject
        return root.getAsJsonObject("responseData")?.get("translatedText")?.stringOrNull()?.trim()?.takeIf { it.isNotEmpty() && !it.startsWith("MYMEMORY WARNING", true) }
    }

    private fun JsonArray.at(i: Int): JsonElement? = if (i < size() && !get(i).isJsonNull) get(i) else null
    private fun JsonElement.asArrayOrNull(): JsonArray? = if (isJsonArray) asJsonArray else null
    private fun JsonElement.stringOrNull(): String? = if (isJsonPrimitive) asString else null
}

/** What was said, split into separate words or phrases: "apple, banana and cherry" → three cards. */
fun splitSpoken(text: String): List<String> =
    text.split(Regex("\\s*(,|;|\\.|\\s+and\\s+|\\s+и\\s+|\\s+und\\s+|\\s+et\\s+|\\s+y\\s+)\\s*", RegexOption.IGNORE_CASE))
        .map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
