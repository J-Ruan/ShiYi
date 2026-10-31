package com.shiyi.memory.platform

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.shiyi.memory.MainActivity
import com.shiyi.memory.MemoryApplication
import com.shiyi.memory.R
import com.shiyi.memory.domain.dueReminder
import java.time.*
import kotlinx.coroutines.*

object Reminders {
    private const val channel = "anniversaries"

    fun cancel(context: Context) {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending(context))
    }

    private fun pending(context: Context) =
        PendingIntent.getBroadcast(
            context,
            42,
            Intent(context, ReminderReceiver::class.java).setAction("com.shiyi.memory.REMIND"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun schedule(context: Context) {
        val now = ZonedDateTime.now()
        var time = now.toLocalDate().atTime(9, 0).atZone(now.zone)
        if (!time.isAfter(now)) time = time.plusDays(1)
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            time.toInstant().toEpochMilli(),
            pending(context),
        )
    }

    suspend fun notifyIfDue(app: MemoryApplication) {
        if (!app.settings.read().notifications) {
            cancel(app)
            return
        }
        if (
            Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
        )
            return
        val records = app.repository.snapshot().anniversaries
        if (records.none { dueReminder(it, LocalDate.now()) }) return
        val manager = app.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(channel, "纪念日提醒", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "仅显示通用提醒，不显示私人内容"
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
        )
        val content =
            PendingIntent.getActivity(
                app,
                0,
                Intent(app, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        manager.notify(
            1,
            NotificationCompat.Builder(app, channel)
                .setSmallIcon(R.drawable.ic_memory)
                .setContentTitle("拾忆 · 温柔提醒")
                .setContentText("有一个值得记住的日子，打开拾忆查看。")
                .setContentIntent(content)
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .build(),
        )
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        val app = context.applicationContext as MemoryApplication
        app.applicationScope.launch {
            try {
                if (app.settings.read().notifications) {
                    if (intent.action == "com.shiyi.memory.REMIND") Reminders.notifyIfDue(app)
                    Reminders.schedule(app)
                }
            } finally {
                result.finish()
            }
        }
    }
}
