package app.mantra.cards

import android.app.Application
import app.mantra.cards.data.Speaker
import app.mantra.cards.data.Store
import app.mantra.cards.system.Daily
import app.mantra.cards.system.MantraWidget
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

class MantraApp : Application() {
    val store by lazy { Store(this) }
    val speaker by lazy { Speaker(this) }

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        Daily.channel(this)
        Daily.schedule(this)
        // The widget follows the answers, a moment after the last one.
        MainScope().launch { store.state.debounce(800).collect { runCatching { MantraWidget.refresh(this@MantraApp) } } }
    }
}
