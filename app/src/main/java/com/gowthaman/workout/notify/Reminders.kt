package com.gowthaman.workout.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.gowthaman.workout.MainActivity
import com.gowthaman.workout.R
import com.gowthaman.workout.data.Prefs
import com.gowthaman.workout.logic.ReminderSchedule
import com.gowthaman.workout.logic.Reminders
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * A daily nudge, scheduled on the device and nowhere else.
 *
 * No Firebase, no push, no server, and no new dependency: AlarmManager is in the platform
 * and the app already has no network permission to send anything with. One alarm is in
 * flight at a time -- it posts a notification, works out the next one and arms that.
 *
 * Inexact on purpose. [AlarmManager.setAndAllowWhileIdle] needs no permission and still
 * fires in Doze, where a plain inexact alarm can be held until the device wakes. The
 * exact-alarm permission would be the wrong thing to ask for: this is a reminder to write
 * something down, not an alarm clock, and a few minutes either side of 20:00 is a reminder
 * at 20:00.
 */
object ReminderAlarm {

    private const val CHANNEL_ID = "reminders"
    private const val NOTIFICATION_ID = 1
    private const val REQUEST_CODE = 1001

    /** Arms the next alarm, or cancels everything when the schedule is off. */
    fun apply(context: Context, schedule: ReminderSchedule) {
        val alarms = context.getSystemService<AlarmManager>() ?: return
        val pending = pendingAlarm(context)

        val next = Reminders.nextTrigger(schedule, LocalDateTime.now())
        if (next == null) {
            alarms.cancel(pending)
            return
        }

        val at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
    }

    /**
     * Posts the nudge.
     *
     * The wording is a question with no claim in it. "You have not logged today" would be
     * the app grading someone from the notification shade, which is the one thing it has
     * promised everywhere else not to do -- and it would often be wrong anyway, since the
     * desktop CLI writes into the same folder.
     */
    fun notifyNow(context: Context) {
        if (!canPost(context)) return
        ensureChannel(context)

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Anything to write down?")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        // Permission is checked above; the platform can still refuse, and a reminder is
        // not worth crashing over.
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }

    /** Android 13 added a runtime permission for this; before that, posting is allowed. */
    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_DEFAULT)
                .apply {
                    description = "The daily nudge you asked for in Settings."
                    setShowBadge(false)
                }
        )
    }

    private fun pendingAlarm(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, ReminderReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/** Fires at the chosen time: posts the nudge, then arms the next one. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val schedule = Prefs(context).reminder.value
        if (!schedule.isActive) return
        ReminderAlarm.notifyNow(context)
        ReminderAlarm.apply(context, schedule)
    }
}

/**
 * Re-arms after a reboot or an app update, both of which drop pending alarms.
 *
 * Without this a reminder set once would work until the next restart and then silently
 * stop, which is worse than never having worked.
 */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
            -> ReminderAlarm.apply(context, Prefs(context).reminder.value)
        }
    }
}
