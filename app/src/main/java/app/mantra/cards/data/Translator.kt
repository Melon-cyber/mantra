package app.mantra.cards.data

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

/** Short translations for new cards, through the public Google Translate web endpoint. */
object Translator {
    suspend fun translate(text: String, from: String, to: String): String = withContext(Dispatchers.IO) {
        val url = URL("https://translate.googleapis.com/translate_a/single?client=gtx&dt=t&sl=$from&tl=$to")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; connectTimeout = 10_000; readTimeout = 15_000
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
        }
        try {
            connection.outputStream.use { it.write("q=${URLEncoder.encode(text, "UTF-8")}".toByteArray()) }
            if (connection.responseCode != 200) throw IOException("Перевод недоступен (${connection.responseCode})")
            val body = connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            val root = JsonParser.parseString(body).asJsonArray
            root[0].asJsonArray.joinToString("") { seg -> seg.asJsonArray[0].takeIf { it.isJsonPrimitive }?.asString.orEmpty() }.trim()
        } finally { connection.disconnect() }
    }
}

/** What was said, split into separate words or phrases: "apple, banana and cherry" → three cards. */
fun splitSpoken(text: String): List<String> =
    text.split(Regex("\\s*(,|;|\\.|\\s+and\\s+|\\s+и\\s+|\\s+und\\s+|\\s+et\\s+|\\s+y\\s+)\\s*", RegexOption.IGNORE_CASE))
        .map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
