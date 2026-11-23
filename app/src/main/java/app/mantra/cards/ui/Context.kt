package app.mantra.cards.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mantra.cards.CardsViewModel
import app.mantra.cards.data.Card
import app.mantra.cards.data.Deck

/** Shown under the answer: other meanings of the word and example sentences with translations. */
@Composable fun CardContext(model: CardsViewModel, card: Card, deck: Deck) {
    if (deck.lang.code == "ru" || card.front.length > 80) return
    val lookups by model.lookups.collectAsStateWithLifecycle()
    val failed by model.lookupFailed.collectAsStateWithLifecycle()
    val key = model.lookupKey(card, deck)
    val l = lookups[key]
    // Loads on its own when the answer is shown again after "undo", too; repeated calls do nothing.
    androidx.compose.runtime.LaunchedEffect(key) { model.context(card, deck) }
    Column(Modifier.fillMaxWidth().padding(top = 16.dp).clip(RoundedCornerShape(18.dp)).background(Elevated).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            l == null && key in failed -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Примеры не загрузились", color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { model.context(card, deck) }) { Text("Ещё раз") }
            }
            l == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(strokeWidth = 2.dp, color = Accent, modifier = Modifier.size(16.dp))
                Text("  Ищем значения и примеры…", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            else -> {
                l.meanings.take(3).forEach { m ->
                    Text(buildAnnotatedString {
                        withStyle(SpanStyle(color = Accent, fontWeight = FontWeight.SemiBold)) { append(m.part + "  ") }
                        append(m.words.take(5).joinToString(", "))
                    }, style = MaterialTheme.typography.bodyMedium)
                }
                if (l.examples.isNotEmpty()) Text("Примеры", style = MaterialTheme.typography.labelMedium, color = Muted, modifier = Modifier.padding(top = 4.dp))
                l.examples.forEach { e ->
                    Column(Modifier.fillMaxWidth().clickable { model.say(e.text, deck) }) {
                        Row(verticalAlignment = Alignment.Top) {
                            Text(highlight(e.text, card.front), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Icon(Icons.AutoMirrored.Rounded.VolumeUp, "Произнести пример", tint = Muted, modifier = Modifier.padding(start = 6.dp).size(18.dp))
                        }
                        if (e.translation.isNotBlank()) Text(e.translation, style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                }
                if (l.meanings.isEmpty() && l.examples.isEmpty()) Text("Для этого слова примеров не нашлось.", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** The studied word in bold inside the example. */
private fun highlight(text: String, word: String) = buildAnnotatedString {
    val w = word.trim().removePrefix("to ").trim()
    val at = if (w.length >= 2) text.indexOf(w, ignoreCase = true) else -1
    if (at < 0) { append(text); return@buildAnnotatedString }
    append(text.substring(0, at))
    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(at, at + w.length)) }
    append(text.substring(at + w.length))
}
