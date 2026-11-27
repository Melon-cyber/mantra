package app.mantra.cards.system

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.mantra.cards.MainActivity
import app.mantra.cards.MantraApp
import app.mantra.cards.R
import app.mantra.cards.data.Saved
import app.mantra.cards.data.Store
import app.mantra.cards.data.plural
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** What is waiting today, for the reminder and the widget. */
data class Today(val waiting: Int, val streak: Int, val doneToday: Int)

fun Store.today(): Today {
    val s: Saved = state.value
    val waiting = s.decks.sumOf { counts(it).let { c -> c.due + c.learning + c.fresh } }
    return Today(waiting, s.streak(), s.log[LocalDate.now().toString()]?.reviews ?: 0)
}

/** Days in a row with at least one answer; today counts once something was answered. */
fun Saved.streak(today: LocalDate = LocalDate.now()): Int =
    generateSequence(if ((log[today.toString()]?.reviews ?: 0) > 0) today else today.minusDays(1)) { it.minusDays(1) }
        .takeWhile { (log[it.toString()]?.reviews ?: 0) > 0 }.take(3650).count()

/** One reminder a day, at a time the learner picks, when there are cards to repeat. */
object Daily {
    private const val CHANNEL = "study"
    private const val PREFS = "reminder"

    data class Setting(val on: Boolean, val hour: Int, val minute: Int) {
        val time: String get() = "%02d:%02d".format(hour, minute)
    }

    fun setting(context: Context): Setting {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Setting(p.getBoolean("on", true), p.getInt("hour", 20), p.getInt("minute", 0))
    }

    fun save(context: Context, value: Setting) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("on", value.on).putInt("hour", value.hour).putInt("minute", value.minute).apply()
        schedule(context)
    }

    fun channel(context: Context) {
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "Напоминание о повторении", NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun pending(context: Context) =
        PendingIntent.getBroadcast(context, 1, Intent(context, DailyReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun schedule(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val s = setting(context)
        if (!s.on) { alarms.cancel(pending(context)); return }
        val now = LocalDateTime.now()
        var at = now.toLocalDate().atTime(LocalTime.of(s.hour, s.minute))
        if (!at.isAfter(now)) at = at.plusDays(1)
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), pending(context))
    }

    fun canNotify(context: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun run(context: Context) {
        val store = (context.applicationContext as MantraApp).store
        MantraWidget.refresh(context)
        if (!setting(context).on || !canNotify(context)) return
        val t = store.today()
        if (t.waiting == 0) return
        val cards = "${t.waiting} ${plural(t.waiting, "карточка ждёт", "карточки ждут", "карточек ждут")}"
        val title = if (t.doneToday == 0 && t.streak > 0) "Не прерывай серию: ${t.streak} ${plural(t.streak, "день", "дня", "дней")} подряд" else "Пора повторить"
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(title)
            .setContentText("$cards. Хватит нескольких минут.")
            .setContentIntent(PendingIntent.getActivity(context, 2,
                Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_STUDY).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE))
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(100, n)
    }
}

class DailyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runCatching { Daily.run(context) }
        Daily.schedule(context)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Daily.schedule(context)
        runCatching { MantraWidget.refresh(context) }
    }
}

/** Home-screen widget: how many cards wait today, the streak, and a button that starts studying. */
class MantraWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context)

    companion object {
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, MantraWidget::class.java))
            if (ids.isEmpty()) return
            val t = (context.applicationContext as MantraApp).store.today()
            val views = RemoteViews(context.packageName, R.layout.widget_mantra)
            if (t.waiting > 0) {
                views.setTextViewText(R.id.label, "Повторить сегодня")
                views.setTextViewText(R.id.count, "${t.waiting} ${plural(t.waiting, "карточка", "карточки", "карточек")}")
            } else {
                views.setTextViewText(R.id.label, "Мантра")
                views.setTextViewText(R.id.count, "На сегодня всё 🎉")
            }
            views.setTextViewText(R.id.detail, when {
                t.streak > 0 -> "🔥 ${t.streak} ${plural(t.streak, "день", "дня", "дней")} подряд" + if (t.doneToday > 0) " · сегодня ${t.doneToday} ${plural(t.doneToday, "ответ", "ответа", "ответов")}" else ""
                else -> "Начни серию сегодня"
            })
            views.setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            views.setOnClickPendingIntent(R.id.study, PendingIntent.getActivity(context, 1,
                Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_STUDY).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE))
            manager.updateAppWidget(ids, views)
        }
    }
}
