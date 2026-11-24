package app.mantra.cards.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mantra.cards.CardsViewModel
import app.mantra.cards.Incoming
import app.mantra.cards.data.Deck
import app.mantra.cards.data.READY_DECKS
import app.mantra.cards.data.Saved
import app.mantra.cards.data.plural
import kotlinx.coroutines.delay

/** Ready-made decks to add with one tap. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ReadyDecksSheet(model: CardsViewModel, state: Saved, close: () -> Unit, open: (Deck) -> Unit) {
    ModalBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Panel) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Готовые колоды", style = MaterialTheme.typography.headlineSmall)
            Text("Добавь колоду и начинай учить. Новые слова приходят понемногу каждый день, сколько — можно поменять в настройках колоды.",
                style = MaterialTheme.typography.bodySmall, color = Muted)
            READY_DECKS.forEach { r ->
                val size = remember(r.file) { model.readySize(r) }
                val have = state.decks.firstOrNull { it.name == r.name }
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Elevated).clickable { open(model.addReady(r)) }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Color(r.color).copy(alpha = .2f)), contentAlignment = Alignment.Center) { Text(r.emoji, fontSize = 22.sp) }
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(r.name, style = MaterialTheme.typography.titleMedium)
                        Text("${r.about} · $size ${plural(size, "карточка", "карточки", "карточек")}", style = MaterialTheme.typography.labelMedium, color = Muted)
                    }
                    Text(if (have != null) "Открыть" else "Добавить", style = MaterialTheme.typography.labelLarge, color = Accent)
                }
            }
        }
    }
}

/**
 * A word that came from another app (selected text, "Share", or Мари): pick a deck, check the translation
 * that Мантра finds by itself and add the card.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable fun QuickAddSheet(model: CardsViewModel, state: Saved, incoming: Incoming, close: (added: Boolean) -> Unit) {
    val decks = state.decks.sortedBy { it.created }
    var deckId by remember { mutableStateOf(model.quickDeckId()?.takeIf { id -> decks.any { it.id == id } } ?: decks.firstOrNull { it.lang.code != "ru" }?.id ?: decks.firstOrNull()?.id) }
    val deck = decks.firstOrNull { it.id == deckId }
    var front by remember { mutableStateOf(incoming.text) }
    var back by remember { mutableStateOf("") }
    var note by remember { mutableStateOf(incoming.context) }
    var backEdited by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    LaunchedEffect(front, deck?.id, deck?.answer) {
        if (deck == null || backEdited) return@LaunchedEffect
        delay(500)
        loading = true
        model.lookupWord(front, deck) { l ->
            loading = false
            if (!backEdited) back = l?.answer(deck.definitions).orEmpty()
            if (incoming.context.isBlank() && l != null) note = l.exampleNote(deck.definitions)
        }
    }
    ModalBottomSheet(onDismissRequest = { close(false) }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Panel) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("В Мантру", style = MaterialTheme.typography.headlineSmall)
            Text("Колода", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                decks.forEach { d ->
                    FilterChip(d.id == deckId, { deckId = d.id }, label = { Text("${d.emoji} ${d.name}", maxLines = 1) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Accent, selectedLabelColor = Ink))
                }
                if (decks.none { it.name == "Мои слова" }) FilterChip(false, { deckId = model.saveDeck(Deck("", "Мои слова", "💬", 0xFF7CC4FF, newPerDay = 10)).id },
                    label = { Text("+ Новая: «Мои слова»") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Accent, selectedLabelColor = Ink))
            }
            OutlinedTextField(front, { front = it }, label = { Text("Слово или фраза") }, modifier = Modifier.fillMaxWidth())
            Box(contentAlignment = Alignment.CenterEnd) {
                OutlinedTextField(back, { back = it; backEdited = true }, label = { Text(if (deck?.definitions == true) "Определение" else "Перевод") }, modifier = Modifier.fillMaxWidth())
                if (loading) CircularProgressIndicator(strokeWidth = 2.dp, color = Accent, modifier = Modifier.padding(end = 12.dp).size(18.dp))
            }
            OutlinedTextField(note, { note = it }, label = { Text("Пример") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                val d = deck ?: return@Button
                model.rememberQuickDeck(d.id)
                model.addPairs(d.id, listOf(front.trim() to back.trim()), listOf(note.trim()))
                close(true)
            }, enabled = deck != null && front.isNotBlank() && back.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink),
                modifier = Modifier.fillMaxWidth()) { Text("Добавить карточку") }
        }
    }
}
