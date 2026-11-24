package app.mantra.cards.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mantra.cards.CardsViewModel
import app.mantra.cards.Session
import app.mantra.cards.data.Card
import app.mantra.cards.data.CardState
import app.mantra.cards.data.Grade
import app.mantra.cards.data.Saved
import app.mantra.cards.data.Scheduler
import app.mantra.cards.data.plural
import app.mantra.cards.data.Typed
import app.mantra.cards.data.checkTyped
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization

private val gradeColors = mapOf(Grade.AGAIN to Color(0xFFFF8A80), Grade.HARD to Color(0xFFFFC266), Grade.GOOD to Color(0xFF6EE7A8), Grade.EASY to Color(0xFF7CC4FF))

@Composable fun StudyScreen(model: CardsViewModel, session: Session, state: Saved) {
    val card = session.card
    var editing by remember { mutableStateOf<Card?>(null) }
    editing?.let { c -> CardEditor(c, close = { editing = null }, save = { saved, _ -> model.editedDuringStudy(model.saveCard(saved)); editing = null },
        delete = { model.deleteCard(c); editing = null; model.study(session.deckId) },
        definitions = state.decks.firstOrNull { it.id == c.deckId }?.definitions ?: false,
        suggest = state.decks.firstOrNull { it.id == c.deckId }?.let { d -> { text: String, done: (app.mantra.cards.data.Lookup?) -> Unit -> model.lookupWord(text, d, done) } }) }
    val deck = state.decks.firstOrNull { it.id == (card?.deckId ?: session.deckId) }
    val left = state.decks.filter { session.deckId == null || it.id == session.deckId }.sumOf { d -> model.counts(d).let { it.due + it.fresh + it.learning } }

    val typing = deck?.typing == true
    var typed by remember(card?.id) { mutableStateOf("") }
    var verdict by remember(card?.id) { mutableStateOf<Typed?>(null) }
    val check = { verdict = card?.let { checkTyped(typed, it.front) } ?: Typed.WRONG; model.reveal() }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = model::endStudy) { Icon(Icons.Rounded.Close, "Закончить") }
            Text(if (card == null) "" else "${deck?.emoji.orEmpty()} ${deck?.name.orEmpty()} · осталось $left", style = MaterialTheme.typography.labelLarge, color = Muted,
                modifier = Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1)
            if (session.last != null) IconButton(onClick = model::undoAnswer) { Icon(Icons.AutoMirrored.Rounded.Undo, "Вернуть прошлую карточку") }
            if (card != null) IconButton(onClick = { editing = card }) { Icon(Icons.Rounded.Edit, "Изменить карточку") }
        }
        if (card == null) {
            Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🎉", fontSize = 56.sp)
                Text("На сегодня всё", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 12.dp))
                Text(if (session.answered > 0) "Ты ответил на ${session.answered} ${plural(session.answered, "карточку", "карточки", "карточек")}. Возвращайся завтра: на главной будет видно, что пора повторить."
                    else "Сейчас повторять нечего. Возвращайся завтра.", color = Muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                Button(onClick = model::endStudy, colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink), modifier = Modifier.padding(top = 24.dp)) { Text("Готово") }
            }
            return@Column
        }
        val turn by animateFloatAsState(if (session.shown) 1f else 0f, tween(260), label = "flip")
        Box(Modifier.fillMaxWidth().weight(1f).padding(vertical = 12.dp).graphicsLayer { rotationX = if (turn < .5f) turn * 30f else (1 - turn) * 30f; cameraDistance = 24 * density }
            .clip(RoundedCornerShape(28.dp)).background(Panel).clickable(enabled = !session.shown && !typing) { model.reveal() }.padding(24.dp)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                if (card.state == CardState.NEW) Text("новая", style = MaterialTheme.typography.labelMedium, color = Color(0xFF7CC4FF))
                if (typing && !session.shown) {
                    Text("Напиши слово", style = MaterialTheme.typography.labelMedium, color = Muted, modifier = Modifier.padding(top = 8.dp))
                    Text(card.back, style = MaterialTheme.typography.headlineSmall.copy(fontSize = if (card.back.length > 60) 20.sp else 26.sp, lineHeight = if (card.back.length > 60) 28.sp else 34.sp),
                        color = Accent, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                    return@Column
                }
                if (typing) verdict?.let { v ->
                    Text(when (v) { Typed.RIGHT -> "✓ Верно"; Typed.ALMOST -> "≈ Почти: «$typed»"; Typed.WRONG -> if (typed.isBlank()) "Не вспомнил — бывает" else "✗ Ты написал: «$typed»" },
                        style = MaterialTheme.typography.labelLarge, color = when (v) { Typed.RIGHT -> Income; Typed.ALMOST -> Warm; Typed.WRONG -> Expense }, textAlign = TextAlign.Center)
                }
                Text(card.front, style = MaterialTheme.typography.headlineSmall.copy(fontSize = if (card.front.length > 60) 20.sp else 28.sp, lineHeight = if (card.front.length > 60) 28.sp else 36.sp),
                    textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                IconButton(onClick = { model.say(card.front, deck) }) { Icon(Icons.AutoMirrored.Rounded.VolumeUp, "Произнести", tint = Muted) }
                if (session.shown) {
                    Box(Modifier.padding(vertical = 14.dp).fillMaxWidth(.4f).height(1.dp).background(Line))
                    Text(card.back, style = MaterialTheme.typography.titleLarge.copy(fontSize = if (card.back.length > 80) 18.sp else 24.sp, lineHeight = if (card.back.length > 80) 26.sp else 32.sp),
                        color = Accent, textAlign = TextAlign.Center)
                    if (card.note.isNotBlank()) Text(card.note, style = MaterialTheme.typography.bodyMedium, color = Muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
                    if (deck != null) CardContext(model, card, deck)
                } else {
                    Spacer(Modifier.height(24.dp))
                    Text("Вспомни ответ и нажми на карточку", style = MaterialTheme.typography.bodySmall, color = Muted, textAlign = TextAlign.Center)
                }
            }
        }
        if (!session.shown && typing) Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            OutlinedTextField(typed, { typed = it }, singleLine = true, placeholder = { Text("Слово на языке колоды") }, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { check() }))
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { typed = ""; check() }, modifier = Modifier.weight(1f)) { Text("Не помню") }
                Button(onClick = check, enabled = typed.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink),
                    modifier = Modifier.weight(2f).height(52.dp)) { Text("Проверить") }
            }
        }
        else if (!session.shown) Button(onClick = model::reveal, colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink),
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(bottom = 4.dp)) { Text("Показать ответ") }
        else Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Grade.entries.forEach { g ->
                val color = gradeColors.getValue(g)
                // After a typed answer, the fitting grade is outlined as a hint.
                val hint = typing && when (verdict) { Typed.RIGHT -> g == Grade.GOOD; Typed.ALMOST -> g == Grade.HARD; Typed.WRONG -> g == Grade.AGAIN; null -> false }
                Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = .16f)).then(if (hint) Modifier.border(2.dp, color, RoundedCornerShape(16.dp)) else Modifier).clickable { model.answer(g) }.padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(g.label, style = MaterialTheme.typography.labelLarge, color = color)
                    Text(Scheduler.preview(card, g), style = MaterialTheme.typography.labelSmall, color = Muted)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
