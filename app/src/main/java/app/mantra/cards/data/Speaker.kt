package app.mantra.cards.data

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Says a word aloud with the phone's speech engine: Latin letters in English, Cyrillic in Russian. */
class Speaker(context: Context) {
    private var ready = false
    private var waiting: String? = null
    private val tts = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) waiting?.let { say(it, waitingTag) }
        waiting = null
    }

    private var waitingTag: String? = null

    /** [tag] is the deck's language ("de-DE"); Cyrillic text is always read in Russian. */
    fun say(text: String, tag: String? = null) {
        if (!ready) { waiting = text; waitingTag = tag; return }
        val latin = text.count { it in 'a'..'z' || it in 'A'..'Z' }
        val cyrillic = text.count { it in 'а'..'я' || it in 'А'..'Я' || it == 'ё' }
        tts.language = if (cyrillic >= latin && cyrillic > 0) Locale.forLanguageTag("ru") else tag?.let(Locale::forLanguageTag) ?: Locale.US
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "card")
    }

    fun release() = runCatching { tts.shutdown() }
}
