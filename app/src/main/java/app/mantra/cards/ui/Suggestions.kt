package app.mantra.cards.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.mantra.cards.data.Lookup

/** What was found for the typed word: tap a meaning or definition for the answer, an example for the hint. */
@Composable fun Suggestions(l: Lookup, definitions: Boolean, pickAnswer: (String) -> Unit, pickNote: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Elevated).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Нажми, чтобы подставить", style = MaterialTheme.typography.labelSmall, color = Muted)
        if (definitions && l.definitions.isNotEmpty()) l.definitions.take(4).forEach { d ->
            Text(buildAnnotatedString {
                withStyle(SpanStyle(color = Accent, fontWeight = FontWeight.SemiBold)) { append(d.part + "  ") }
                append(d.text)
            }, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().clickable { pickAnswer(d.text) })
        } else {
            if (l.translation.isNotBlank()) Text(l.translation, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { pickAnswer(l.translation) })
            l.meanings.take(3).forEach { m ->
                Text(buildAnnotatedString {
                    withStyle(SpanStyle(color = Accent, fontWeight = FontWeight.SemiBold)) { append(m.part + "  ") }
                    append(m.words.take(5).joinToString(", "))
                }, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().clickable { pickAnswer(m.words.take(3).joinToString(", ")) })
            }
        }
        val examples = l.examples.map { if (!definitions && it.translation.isNotBlank()) "${it.text} — ${it.translation}" else it.text } +
            (if (l.examples.isEmpty()) l.definitions.mapNotNull { it.example.takeIf { e -> e.isNotBlank() } } else emptyList())
        if (examples.isNotEmpty()) Text("Примеры", style = MaterialTheme.typography.labelSmall, color = Muted, modifier = Modifier.padding(top = 4.dp))
        examples.take(3).forEach { e -> Text(e, style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic), modifier = Modifier.fillMaxWidth().clickable { pickNote(e) }) }
    }
}
