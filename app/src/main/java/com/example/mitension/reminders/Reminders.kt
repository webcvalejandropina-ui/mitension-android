package com.example.mitension.reminders

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.media.*
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.mitension.*
import com.example.mitension.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate

/** Configuration is saved even if notification or exact-alarm permission is denied. */
object Reminders {
    private fun prefs(context: Context) = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)
    fun load(context: Context): ReminderConfig = runCatching {
        Json.decodeFromString<ReminderConfig>(prefs(context).getString("config", null) ?: return ReminderConfig())
    }.getOrDefault(ReminderConfig())
    fun save(context: Context, config: ReminderConfig) {
        require(config.morning.valid() && config.evening.valid())
        check(prefs(context).edit().putString("config", Json.encodeToString(config)).commit())
        reconcile(context)
    }
    fun exactAllowed(context: Context) = Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    fun notificationsAllowed(context: Context) = NotificationManagerCompatHelper.allowed(context)
    private fun operation(context: Context, key: String, plan: ReminderPlan? = null): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).setAction("com.example.mitension.$key")
        if (plan != null) intent.putExtra("period", plan.period).putExtra("day", plan.day.toString())
            .putExtra("followup", plan.followup).putExtra("alarm", plan.alarm)
        return PendingIntent.getBroadcast(context, key.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Only four stable pending-intent keys are owned. Reboot/time changes restore the next events. */
    @Synchronized fun reconcile(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val plans = reminderPlans(load(context), context.store.readings.value).associateBy { it.key }
        for (period in listOf("morning", "evening")) for (kind in listOf("primary", "followup")) {
            val key = "$period.$kind"
            manager.cancel(operation(context, key))
            val plan = plans[key] ?: continue
            val pending = operation(context, key, plan)
            if (exactAllowed(context)) {
                if (plan.alarm) manager.setAlarmClock(AlarmManager.AlarmClockInfo(plan.at, open(context)), pending)
                else manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, plan.at, pending)
            } else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, plan.at, pending)
        }
    }
    fun open(context: Context): PendingIntent = PendingIntent.getActivity(context, 0,
        Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    fun channels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("readings", context.t("Alertas"), NotificationManager.IMPORTANCE_DEFAULT))
        manager.createNotificationChannel(NotificationChannel("alarm", context.t("Alarma sonora"), NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null, null) // Service owns the continuous sound, avoiding two simultaneous tones.
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        })
    }
    fun notify(context: Context, followup: Boolean) {
        channels(context)
        if (!notificationsAllowed(context)) return
        val message = if (followup) "Han pasado 30 minutos y no has guardado la toma de este periodo. Regístrala cuando puedas."
            else "Descansa unos minutos y registra tu toma en Mi Tensión."
        context.getSystemService(NotificationManager::class.java).notify(if (followup) 11 else 10,
            NotificationCompat.Builder(context, "readings").setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(context.t("Hora de tomar la tensión")).setContentText(context.t(message))
                .setContentIntent(open(context)).setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build())
    }
}

private object NotificationManagerCompatHelper {
    fun allowed(context: Context): Boolean = context.getSystemService(NotificationManager::class.java).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
}

/** The follow-up rechecks actual saved data at delivery, not only when initially planned. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val period = intent.getStringExtra("period") ?: return
        val config = Reminders.load(context)
        val schedule = if (period == "morning") config.morning else config.evening
        if (!schedule.enabled) return
        val day = runCatching { LocalDate.parse(intent.getStringExtra("day")) }.getOrNull() ?: return
        val followup = intent.getBooleanExtra("followup", false)
        val satisfied = context.store.readings.value.any { it.period() == period && it.localTime().toLocalDate() == day }
        if (!followup || !satisfied) {
            Reminders.notify(context, followup)
            if (!followup && schedule.alarm && Reminders.exactAllowed(context))
                ContextCompat.startForegroundService(context, Intent(context, AlarmSoundService::class.java))
        }
        runCatching { Reminders.reconcile(context) }
    }
}

class RestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if(intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED, AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)) return
        runCatching { Reminders.reconcile(context) }
    }
}

/** Optional looping alarm has a visible Stop action, no full-screen takeover, and a five-minute safety cap. */
class AlarmSoundService : Service() {
    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "stop") { stopSelf(); return START_NOT_STICKY }
        Reminders.channels(this)
        val stop = PendingIntent.getService(this, 22, Intent(this, AlarmSoundService::class.java).setAction("stop"), PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, "alarm").setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(t("Hora de tomar la tensión")).setContentText(t("Descansa unos minutos y registra tu toma en Mi Tensión."))
            .setOngoing(true).setContentIntent(Reminders.open(this)).addAction(0, t("Detener alarma"), stop).build()
        startForeground(20, notification)
        if (player == null) runCatching {
            player = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
                setDataSource(this@AlarmSoundService, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                isLooping = true; prepare(); start()
            }
        }.onFailure { stopSelf() }
        handler.removeCallbacksAndMessages(null); handler.postDelayed({ stopSelf() }, 300000)
        return START_NOT_STICKY
    }
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); player?.release(); player = null; super.onDestroy() }
}
