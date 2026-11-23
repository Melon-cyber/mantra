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
        if (ready) waiting?.let { say(it) }
        waiting = null
    }

    fun say(text: String) {
        if (!ready) { waiting = text; return }
        val latin = text.count { it in 'a'..'z' || it in 'A'..'Z' }
        val cyrillic = text.count { it in 'а'..'я' || it in 'А'..'Я' || it == 'ё' }
        tts.language = if (latin > cyrillic) Locale.US else Locale.forLanguageTag("ru")
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "card")
    }

    fun release() = runCatching { tts.shutdown() }
}
