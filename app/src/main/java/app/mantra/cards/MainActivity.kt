package app.mantra.cards

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import app.mantra.cards.ui.MantraRoot
import app.mantra.cards.ui.MantraTheme

class MainActivity : ComponentActivity() {
    private val model: CardsViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Opened from another app just to add a word: go back there once the card is added.
        val shared = savedInstanceState == null && receive(intent)
        setContent { MantraTheme { MantraRoot(model, sharedDone = { if (shared) finish() }) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receive(intent)
    }

    private fun receive(intent: Intent?): Boolean {
        val text = when (intent?.action) {
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        } ?: return false
        return model.receive(text, intent?.getStringExtra(EXTRA_CONTEXT))
    }

    companion object {
        /** The sentence the word was taken from (Мари sends it), saved as the card's example. */
        const val EXTRA_CONTEXT = "app.mantra.cards.CONTEXT"
    }
}
