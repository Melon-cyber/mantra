package app.mantra.cards

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
        setContent { MantraTheme { MantraRoot(model) } }
    }
}
