package app.mantra.cards

import android.app.Application
import app.mantra.cards.data.Speaker
import app.mantra.cards.data.Store

class MantraApp : Application() {
    val store by lazy { Store(this) }
    val speaker by lazy { Speaker(this) }
}
