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
import com.gowthaman.workout.logic.Reminder
import com.gowthaman.workout.logic.ReminderSettings
import com.gowthaman.workout.logic.Reminders
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Daily nudges, scheduled on the device and nowhere else.
 *
 * No Firebase, no push, no server and no new dependency: AlarmManager is in the platform
 * and the app has no network permission to send anything with. Each reminder owns one
 * pending alarm and one notification slot, both addressed by its id -- so editing a
 * reminder replaces its alarm rather than adding a second, and breakfast cannot overwrite
 * dinner's notification.
 *
 * Inexact on purpose. [AlarmManager.setAndAllowWhileIdle] needs no permission and still
 * fires in Doze, where a plain inexact alarm can be held until the device wakes. The
 * exact-alarm permission would be the wrong thing to ask for: this is a reminder to write
 * something down, not an alarm clock.
 */
object ReminderAlarm {

    private const val CHANNEL_ID = "reminders"

    /**
     * Ids address pending alarms, so they are offset away from zero rather than used raw.
     * A request code of 0 is the kind of value other code reaches for by accident.
     */
    private const val ID_BASE = 2000

    /** Re-arms every reminder, and cancels the alarm of every one that should not fire. */
    fun applyAll(context: Context, settings: ReminderSettings) {
        val active = settings.active.map { it.id }.toSet()
        settings.reminders.forEach { reminder ->
            if (reminder.id in active) schedule(context, reminder) else cancel(context, reminder.id)
        }
    }

    fun cancel(context: Context, id: Int) {
        val alarms = context.getSystemService<AlarmManager>() ?: return
        alarms.cancel(pendingAlarm(context, id))
    }

    private fun schedule(context: Context, reminder: Reminder) {
        val alarms = context.getSystemService<AlarmManager>() ?: return
        val next = Reminders.nextTrigger(reminder, LocalDateTime.now())
        val pending = pendingAlarm(context, reminder.id)
        if (next == null) {
            alarms.cancel(pending)
            return
        }
        val at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
    }

    /**
     * Posts one nudge.
     *
     * The wording is a question with no claim in it. "You have not logged today" would be
     * the app grading someone from the notification shade, which it has promised
     * everywhere else not to do -- and it would often be wrong anyway, since the desktop
     * CLI writes into the same folder.
     */
    fun notifyFor(context: Context, reminder: Reminder) {
        if (!canPost(context)) return
        ensureChannel(context)

        val open = PendingIntent.getActivity(
            context,
            reminder.id,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(reminder.title)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        // A named reminder says which one it is on the first line and stays neutral on the
        // second. An unnamed one has already used the question as its title.
        if (reminder.label.isNotBlank()) builder.setContentText("Anything to write down?")

        // Permission is checked above; the platform can still refuse, and a reminder is
        // not worth crashing over.
        runCatching {
            NotificationManagerCompat.from(context).notify(ID_BASE + reminder.id, builder.build())
        }
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
                    description = "The nudges you asked for in Settings."
                    setShowBadge(false)
                }
        )
    }

    private fun pendingAlarm(context: Context, id: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        ID_BASE + id,
        Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_ID, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    const val EXTRA_ID = "reminder_id"
}

/**
 * Fires at one reminder's time: posts its nudge, then arms its next one.
 *
 * Looking the reminder up by id rather than trusting the intent is what makes a deleted
 * reminder safe. Its alarm may still be pending; it arrives, finds nothing, and stops --
 * no notification, and nothing re-armed.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val settings = Prefs(context).reminders.value
        if (!settings.enabled) return
        val id = intent.getIntExtra(ReminderAlarm.EXTRA_ID, -1)
        val reminder = settings.reminders.firstOrNull { it.id == id } ?: return
        if (!reminder.isActive) return

        ReminderAlarm.notifyFor(context, reminder)
        ReminderAlarm.applyAll(context, settings)
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
            -> ReminderAlarm.applyAll(context, Prefs(context).reminders.value)
        }
    }
}
