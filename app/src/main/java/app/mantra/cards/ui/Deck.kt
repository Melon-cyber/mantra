package app.mantra.cards.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mantra.cards.CardsViewModel
import app.mantra.cards.data.COLORS
import app.mantra.cards.data.Card
import app.mantra.cards.data.CardState
import app.mantra.cards.data.Deck
import app.mantra.cards.data.EMOJIS
import app.mantra.cards.data.Language
import app.mantra.cards.data.Lookup
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import app.mantra.cards.data.Saved
import app.mantra.cards.data.parseCards
import app.mantra.cards.data.plural
import kotlinx.coroutines.launch

@Composable fun DeckScreen(model: CardsViewModel, deck: Deck, state: Saved, back: () -> Unit, edit: () -> Unit) {
    val counts = model.counts(deck)
    val cards = state.cards.filter { it.deckId == deck.id }
    var query by remember { mutableStateOf("") }
    var cardEditor by remember { mutableStateOf<Card?>(null) }
    var importing by remember { mutableStateOf(false) }
    var voice by remember { mutableStateOf(false) }
    if (voice) VoiceAddSheet(model, deck, close = { voice = false })
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val shown = cards.filter { query.isBlank() || it.front.contains(query, true) || it.back.contains(query, true) }.sortedByDescending { it.created }

    cardEditor?.let { c -> CardEditor(c, close = { cardEditor = null }, save = { saved, again -> model.saveCard(saved); cardEditor = if (again) Card("", deck.id, "", "") else null },
        delete = if (c.id.isNotBlank()) ({ model.deleteCard(c); cardEditor = null }) else null,
        definitions = deck.definitions, suggest = { text, done -> model.lookupWord(text, deck, done) }) }
    if (importing) ImportDialog(model, close = { importing = false }) { text -> model.addText(deck.id, text); importing = false }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Удалить «${deck.name}»?") },
        text = { Text("Пропадут все ${cards.size} ${plural(cards.size, "карточка", "карточки", "карточек")} и их прогресс.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; back(); model.deleteDeck(deck) }) { Text("Удалить") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } })

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") }
                Row(Modifier.weight(1f)) {}
                IconButton(onClick = edit) { Icon(Icons.Rounded.Edit, "Изменить колоду") }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Ещё") }
                    DropdownMenu(menu, { menu = false }) { DropdownMenuItem(text = { Text("Удалить колоду") }, onClick = { menu = false; confirmDelete = true }) }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(Color(deck.color).copy(alpha = .2f)), contentAlignment = Alignment.Center) { Text(deck.emoji, fontSize = 28.sp) }
                    Column(Modifier.padding(start = 14.dp)) {
                        Text(deck.name, style = MaterialTheme.typography.headlineSmall)
                        Text("${cards.size} ${plural(cards.size, "карточка", "карточки", "карточек")} · выучено ${cards.count { it.state == CardState.REVIEW && it.interval >= 21 }}",
                            color = Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stat("${counts.due + counts.learning}", "повторить", Accent, Modifier.weight(1f))
                    Stat("${counts.fresh}", "новых сегодня", Color(0xFF7CC4FF), Modifier.weight(1f))
                    Stat("${cards.count { it.state == CardState.NEW }}", "ещё не начато", Muted, Modifier.weight(1f))
                }
                Button(onClick = { model.study(deck.id) }, enabled = counts.due + counts.fresh + counts.learning > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink), modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
                    Text(if (cards.isEmpty()) "Сначала добавь карточки" else if (counts.due + counts.fresh + counts.learning == 0) "На сегодня всё 🎉" else "Учить")
                }
                Button(onClick = { voice = true }, colors = ButtonDefaults.buttonColors(containerColor = Elevated, contentColor = SoftWhite), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Icon(Icons.Rounded.Mic, null); Text("  Добавить голосом", style = MaterialTheme.typography.labelLarge)
                }
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { cardEditor = Card("", deck.id, "", "") }, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.Add, null); Text("Карточка") }
                    OutlinedButton(onClick = { importing = true }, modifier = Modifier.weight(1f)) { Text("Списком") }
                }
            }
        }
        if (cards.size > 8) item {
            OutlinedTextField(query, { query = it }, singleLine = true, leadingIcon = { Icon(Icons.Rounded.Search, null) }, placeholder = { Text("Найти карточку") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp))
        }
        items(shown, key = { it.id }) { c ->
            Row(Modifier.fillMaxWidth().clickable { cardEditor = c }.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(c.front, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(c.back, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(when (c.state) {
                    CardState.NEW -> "новая"
                    CardState.LEARNING, CardState.RELEARNING -> "учится"
                    CardState.REVIEW -> if (c.interval >= 21) "выучена" else "через ${c.interval} дн."
                }, style = MaterialTheme.typography.labelSmall, color = if (c.state == CardState.REVIEW && c.interval >= 21) Accent else Muted)
            }
            HorizontalDivider(color = Line, modifier = Modifier.padding(horizontal = 20.dp))
        }
    }
}

@Composable private fun Stat(value: String, label: String, color: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Panel).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Muted, textAlign = TextAlign.Center)
    }
}

@Composable fun CardEditor(start: Card, close: () -> Unit, save: (Card, Boolean) -> Unit, delete: (() -> Unit)?,
    definitions: Boolean = false, suggest: ((String, (Lookup?) -> Unit) -> Unit)? = null) {
    var front by remember(start) { mutableStateOf(start.front) }
    var back by remember(start) { mutableStateOf(start.back) }
    var note by remember(start) { mutableStateOf(start.note) }
    var found by remember(start) { mutableStateOf<Lookup?>(null) }
    var looking by remember(start) { mutableStateOf(false) }
    var autoBack by remember(start) { mutableStateOf(start.back.isBlank()) }
    var autoNote by remember(start) { mutableStateOf(start.note.isBlank()) }
    // While typing a word, look it up after a short pause and fill what is still empty.
    LaunchedEffect(front) {
        if (suggest == null || front.isBlank() || front == start.front && start.id.isNotBlank()) return@LaunchedEffect
        delay(700)
        looking = true
        val word = front
        suggest(word) { l ->
            looking = false
            if (front != word) return@suggest
            found = l
            if (l != null) {
                if (autoBack || back.isBlank()) { back = l.answer(definitions); autoBack = true }
                if (autoNote || note.isBlank()) { note = l.exampleNote(definitions); autoNote = true }
            }
        }
    }
    val isNew = start.id.isBlank()
    val ok = front.isNotBlank() && back.isNotBlank()
    AlertDialog(onDismissRequest = close, title = { Text(if (isNew) "Новая карточка" else "Карточка") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(front, { front = it.take(500) }, label = { Text("Вопрос или слово") }, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                OutlinedTextField(back, { back = it.take(1000); autoBack = false }, label = { Text(if (definitions) "Определение" else "Перевод или ответ") }, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    trailingIcon = if (suggest != null && front.isNotBlank()) ({ IconButton(onClick = { looking = true; suggest(front) { l -> looking = false; found = l; l?.let { back = it.answer(definitions); autoBack = true } } }) {
                        Icon(Icons.Rounded.Translate, if (definitions) "Найти определение" else "Перевести") } }) else null)
                OutlinedTextField(note, { note = it.take(1000); autoNote = false }, label = { Text("Пример или подсказка (необязательно)") }, modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp))
                if (looking) Text(if (definitions) "Ищем определение и примеры…" else "Ищем перевод и примеры…", color = Muted, style = MaterialTheme.typography.bodySmall)
                found?.let { l -> Suggestions(l, definitions, pickAnswer = { back = it; autoBack = false }, pickNote = { note = it; autoNote = false }) }
                if (isNew) TextButton(onClick = { save(start.copy(front = front, back = back, note = note), true) }, enabled = ok) { Text("Сохранить и добавить ещё") }
                if (delete != null) TextButton(onClick = delete) { Text("Удалить карточку", color = Color(0xFFFF8A80)) }
            }
        },
        confirmButton = { TextButton(onClick = { save(start.copy(front = front, back = back, note = note), false) }, enabled = ok) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = close) { Text("Отмена") } })
}

@Composable private fun ImportDialog(model: CardsViewModel, close: () -> Unit, add: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val file = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { scope.launch { model.readText(it)?.let { t -> text = t } } } }
    val count = remember(text) { parseCards(text).size }
    AlertDialog(onDismissRequest = close, title = { Text("Добавить списком") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Каждая строка — карточка: сначала вопрос, потом ответ через тире, точку с запятой или табуляцию. Подойдёт и текстовый экспорт из Anki или Quizlet.",
                    color = Muted, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(text, { text = it }, placeholder = { Text("apple — яблоко\nbook — книга") }, modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp))
                TextButton(onClick = { file.launch(arrayOf("text/*", "*/*")) }) { Text("Взять из файла") }
                if (text.isNotBlank()) Text(if (count == 0) "Не получилось разобрать строки" else "Получится карточек: $count", style = MaterialTheme.typography.labelMedium, color = if (count == 0) Color(0xFFFF8A80) else Accent)
            }
        },
        confirmButton = { TextButton(onClick = { add(text) }, enabled = count > 0) { Text("Добавить") } },
        dismissButton = { TextButton(onClick = close) { Text("Отмена") } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun DeckEditor(start: Deck, close: () -> Unit, save: (Deck) -> Unit) {
    var d by remember { mutableStateOf(start) }
    AlertDialog(onDismissRequest = close, title = { Text(if (start.id.isBlank()) "Новая колода" else "Колода") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(d.name, { d = d.copy(name = it.take(60)) }, label = { Text("Название") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    EMOJIS.forEach { e ->
                        Box(Modifier.size(38.dp).clip(CircleShape).background(if (e == d.emoji) Color(d.color).copy(alpha = .35f) else Elevated).clickable { d = d.copy(emoji = e) }
                            .semantics { selected = e == d.emoji }, contentAlignment = Alignment.Center) { Text(e, fontSize = 18.sp) }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    COLORS.forEachIndexed { i, c ->
                        Box(Modifier.size(28.dp).clip(CircleShape).background(Color(c)).then(if (c == d.color) Modifier.border(3.dp, SoftWhite, CircleShape) else Modifier)
                            .clickable { d = d.copy(color = c) }.semantics { contentDescription = "Цвет ${i + 1}"; selected = c == d.color })
                    }
                }
                Text("Язык (для голоса и перевода)", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Language.entries.filter { it != Language.RU }.forEach { l ->
                        FilterChip(d.language == l.name, { d = d.copy(language = l.name) }, label = { Text(l.label) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Accent, selectedLabelColor = Ink))
                    }
                }
                Text("Ответ на карточке", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(d.answer != "DEFINITION", { d = d.copy(answer = "TRANSLATION") }, label = { Text("Перевод на русский") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Accent, selectedLabelColor = Ink))
                    FilterChip(d.answer == "DEFINITION", { d = d.copy(answer = "DEFINITION") }, label = { Text(if (d.lang == Language.EN) "Английские определения" else "Определения на языке") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Accent, selectedLabelColor = Ink))
                }
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Elevated).clickable { d = d.copy(typing = !d.typing) }.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Писать слово самому")
                        Text(if (d.definitions) "Показываю определение — пишешь слово" else "Показываю перевод — пишешь слово", style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                    androidx.compose.material3.Switch(d.typing, { d = d.copy(typing = it) })
                }
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Elevated).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Новых в день", modifier = Modifier.weight(1f))
                    IconButton(onClick = { d = d.copy(newPerDay = (d.newPerDay - 5).coerceAtLeast(0)) }) { Icon(Icons.Rounded.Remove, "Меньше") }
                    Text("${d.newPerDay}", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.width(32.dp))
                    IconButton(onClick = { d = d.copy(newPerDay = (d.newPerDay + 5).coerceAtMost(200)) }) { Icon(Icons.Rounded.Add, "Больше") }
                }
            }
        },
        confirmButton = { TextButton(onClick = { save(d) }, enabled = d.name.isNotBlank()) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = close) { Text("Отмена") } })
}
