package app.mantra.cards.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.mantra.cards.CardsViewModel
import app.mantra.cards.data.Deck
import app.mantra.cards.data.Language
import app.mantra.cards.data.splitSpoken

private data class Draft(val id: Long, val front: String, val back: String, val loading: Boolean = true, val note: String = "")

/**
 * Adding cards by voice: say a word (or several, with "and" or pauses that become commas),
 * Mantra translates each one and shows the cards to check before they are saved.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable fun VoiceAddSheet(model: CardsViewModel, deck: Deck, close: () -> Unit) {
    var language by remember { mutableStateOf(deck.lang) }
    var speakRussian by remember { mutableStateOf(false) }
    var nonstop by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val drafts = remember { mutableStateListOf<Draft>() }
    var counter by remember { mutableStateOf(0L) }
    val target = if (language == Language.RU) Language.EN else language
    val launcherHolder = remember { arrayOfNulls<() -> Unit>(1) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val heard = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (result.resultCode != Activity.RESULT_OK || heard.isNullOrBlank()) return@rememberLauncherForActivityResult
        error = null
        val learning = deck.copy(language = target.name)
        splitSpoken(heard).forEach { word ->
            val id = ++counter
            if (speakRussian) {
                drafts.add(0, Draft(id, "…", word))
                model.translate(word, learning, toForeign = true) { t -> val i = drafts.indexOfFirst { it.id == id }; if (i >= 0) drafts[i] = drafts[i].copy(front = t ?: "", loading = false) }
            } else {
                drafts.add(0, Draft(id, word, "…"))
                model.lookupWord(word, learning) { l ->
                    val i = drafts.indexOfFirst { it.id == id }
                    if (i >= 0) drafts[i] = drafts[i].copy(back = l?.answer(deck.definitions).orEmpty(), note = l?.exampleNote(deck.definitions).orEmpty(), loading = false)
                }
            }
        }
        if (nonstop) launcherHolder[0]?.invoke()
    }
    val listen: () -> Unit = {
        val tag = if (speakRussian) Language.RU.speech else target.speech
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag).putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, tag)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, if (speakRussian) "Скажи слово по-русски" else "Скажи слово: ${target.label.lowercase()}")
        try { launcher.launch(intent) } catch (_: ActivityNotFoundException) {
            error = "На телефоне нет распознавания речи. Установи приложение Google или включи голосовой ввод в настройках."
        }
    }
    launcherHolder[0] = listen

    ModalBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Panel) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Карточки голосом", style = MaterialTheme.typography.headlineSmall)
            Text("Язык колоды", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Language.entries.filter { it != Language.RU }.forEach { l ->
                    FilterChip(target == l, { language = l; if (deck.language != l.name) model.saveDeck(deck.copy(language = l.name)) }, label = { Text(l.label) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Accent, selectedLabelColor = Ink))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Говорю по-русски, перевод на ${target.label.lowercase()}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(speakRussian, { speakRussian = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Слушать без остановки", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(nonstop, { nonstop = it })
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.size(84.dp).clip(CircleShape).background(Accent).clickable(onClick = listen).semantics { contentDescription = "Начать диктовку" }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Mic, null, tint = Ink, modifier = Modifier.size(40.dp))
                }
            }
            Text(if (drafts.isEmpty()) "Нажми на микрофон и скажи слово. Несколько слов разделяй словом «and» или «и» — каждое станет карточкой. Чтобы закончить, просто закрой окно распознавания."
                else "Проверь карточки: перевод можно поправить.", style = MaterialTheme.typography.bodySmall, color = Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = Expense, style = MaterialTheme.typography.bodySmall) }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(drafts, key = { _, d -> d.id }) { i, d ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(d.front, { drafts[i] = d.copy(front = it) }, singleLine = true, modifier = Modifier.weight(1f))
                        Text("—", modifier = Modifier.padding(horizontal = 6.dp), color = Muted)
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                            OutlinedTextField(d.back, { drafts[i] = d.copy(back = it) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            if (d.loading) CircularProgressIndicator(strokeWidth = 2.dp, color = Accent, modifier = Modifier.padding(end = 10.dp).size(16.dp))
                        }
                        IconButton(onClick = { drafts.removeAt(i) }) { Icon(Icons.Rounded.Close, "Убрать") }
                    }
                }
            }
            val ready = drafts.filter { it.front.isNotBlank() && it.back.isNotBlank() && it.front != "…" && it.back != "…" }
            Button(onClick = { val list = ready.reversed(); model.addPairs(deck.id, list.map { it.front.trim() to it.back.trim() }, list.map { it.note }); close() }, enabled = ready.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink), modifier = Modifier.fillMaxWidth()) {
                Text(if (ready.isEmpty()) "Добавить" else "Добавить ${ready.size}")
            }
        }
    }
}
