package app.mantra.cards.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mantra.cards.CardsViewModel
import app.mantra.cards.data.CardState
import app.mantra.cards.data.Deck
import app.mantra.cards.data.Saved
import app.mantra.cards.data.Scheduler
import app.mantra.cards.data.plural
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

val RU: Locale = Locale.forLanguageTag("ru")

@Composable fun MantraRoot(model: CardsViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val session by model.session.collectAsStateWithLifecycle()
    val note by model.note.collectAsStateWithLifecycle()
    var deckId by rememberSaveable { mutableStateOf<String?>(null) }
    var deckEditor by remember { mutableStateOf<Deck?>(null) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(note) {
        val n = note ?: return@LaunchedEffect
        snackbar.currentSnackbarData?.dismiss()
        val result = snackbar.showSnackbar(n.text, actionLabel = if (n.undo != null) "Вернуть" else null, duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) n.undo?.invoke()
        model.note(null)
    }
    BackHandler(enabled = session != null || deckId != null) { if (session != null) model.endStudy() else deckId = null }
    deckEditor?.let { d -> DeckEditor(d, close = { deckEditor = null }) { saved -> val s = model.saveDeck(saved); deckEditor = null; if (d.id.isBlank()) deckId = s.id } }

    val deck = state.decks.firstOrNull { it.id == deckId }
    Scaffold(containerColor = Ink, snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (session == null && deck == null) ExtendedFloatingActionButton(onClick = { deckEditor = Deck("", "") }, containerColor = Accent, contentColor = Ink,
                icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Колода") })
        }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                session != null -> StudyScreen(model, session!!, state)
                deck != null -> DeckScreen(model, deck, state, back = { deckId = null }, edit = { deckEditor = deck })
                else -> HomeScreen(model, state, open = { deckId = it.id })
            }
        }
    }
}

@Composable private fun HomeScreen(model: CardsViewModel, state: Saved, open: (Deck) -> Unit) {
    val counts = state.decks.associate { it.id to model.counts(it) }
    val due = counts.values.sumOf { it.due + it.learning }
    val fresh = counts.values.sumOf { it.fresh }
    val today = LocalDate.now()
    val streak = generateSequence(if ((state.log[today.toString()]?.reviews ?: 0) > 0) today else today.minusDays(1)) { it.minusDays(1) }
        .takeWhile { (state.log[it.toString()]?.reviews ?: 0) > 0 }.take(3650).count()
    var menu by remember { mutableStateOf(false) }
    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(model::backup) }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(model::restore) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        item {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 4.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Мантра", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Ещё") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("Сохранить копию в файл") }, onClick = { menu = false; backup.launch("mantra-$today.json") })
                        DropdownMenuItem(text = { Text("Восстановить из файла") }, onClick = { menu = false; restore.launch(arrayOf("application/json", "*/*")) })
                    }
                }
            }
        }
        if (state.decks.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(20.dp).clip(RoundedCornerShape(22.dp)).background(Panel).padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("🧠", fontSize = 34.sp)
                Text("Запоминай навсегда", style = MaterialTheme.typography.titleLarge)
                Text("Мантра показывает карточку как раз тогда, когда ты начинаешь её забывать. Пара минут в день — и слова, даты, термины остаются в памяти надолго.\n\nСоздай колоду и добавь карточки по одной или списком, например «cat — кошка» на каждой строке.",
                    color = Muted, style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { open(model.starterDeck()) }) { Text("Попробовать: 50 английских слов") }
            }
        } else {
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp).clip(RoundedCornerShape(24.dp)).background(Panel).padding(18.dp)) {
                    Text(if (due + fresh == 0) "На сегодня всё 🎉" else "Сегодня", style = MaterialTheme.typography.titleLarge)
                    Text(when {
                        due + fresh == 0 -> "Новые повторения появятся завтра."
                        else -> listOfNotNull(due.takeIf { it > 0 }?.let { "повторить $it" }, fresh.takeIf { it > 0 }?.let { "новых $it" }).joinToString(" · ")
                    }, color = Muted, style = MaterialTheme.typography.bodyMedium)
                    if (streak > 0) Text("🔥 $streak ${plural(streak, "день", "дня", "дней")} подряд · сегодня ${state.log[today.toString()]?.reviews ?: 0} ${plural(state.log[today.toString()]?.reviews ?: 0, "ответ", "ответа", "ответов")}",
                        style = MaterialTheme.typography.labelMedium, color = Accent, modifier = Modifier.padding(top = 6.dp))
                    if (due + fresh > 0) Button(onClick = { model.study(null) }, colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink),
                        modifier = Modifier.padding(top = 14.dp).fillMaxWidth()) { Text("Учить все колоды") }
                    Forecast(state)
                }
            }
            item { Text("Колоды", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp)) }
            items(state.decks.sortedBy { it.created }, key = { it.id }) { d ->
                val c = counts[d.id]!!
                Row(Modifier.fillMaxWidth().clickable { open(d) }.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Color(d.color).copy(alpha = .2f)), contentAlignment = Alignment.Center) { Text(d.emoji, fontSize = 22.sp) }
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(d.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${c.total} ${plural(c.total, "карточка", "карточки", "карточек")}", style = MaterialTheme.typography.labelMedium, color = Muted)
                    }
                    if (c.due + c.learning > 0) Badge("${c.due + c.learning}", Accent)
                    if (c.fresh > 0) Badge("${c.fresh}", Color(0xFF7CC4FF))
                }
            }
        }
    }
}

@Composable fun Badge(text: String, color: Color) {
    Box(Modifier.padding(start = 6.dp).clip(CircleShape).background(color.copy(alpha = .2f)).padding(horizontal = 10.dp, vertical = 3.dp)) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** How many reviews come in the next seven days. */
@Composable private fun Forecast(state: Saved) {
    val today = LocalDate.now()
    val days = (1..7).map { today.plusDays(it.toLong()) }
    val counts = days.map { d ->
        val from = Scheduler.startOfDay(d); val to = Scheduler.startOfDay(d.plusDays(1))
        state.cards.count { it.state == CardState.REVIEW && it.due in from until to }
    }
    if (counts.all { it == 0 }) return
    val max = counts.max().coerceAtLeast(1)
    Text("Повторения на неделю вперёд", style = MaterialTheme.typography.labelMedium, color = Muted, modifier = Modifier.padding(top = 16.dp))
    Row(Modifier.fillMaxWidth().height(64.dp).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        days.zip(counts).forEach { (d, n) ->
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = "${d.format(DateTimeFormatter.ofPattern("EEEE", RU))}: $n" },
                horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.fillMaxWidth().height(36.dp)) {
                    val h = size.height * (n.toFloat() / max).coerceAtLeast(.04f)
                    drawRoundRect(Accent.copy(alpha = .75f), Offset(0f, size.height - h), Size(size.width, h), CornerRadius(6f, 6f))
                }
                Text(d.format(DateTimeFormatter.ofPattern("EE", RU)).take(2), style = MaterialTheme.typography.labelSmall, color = Muted)
            }
        }
    }
}
