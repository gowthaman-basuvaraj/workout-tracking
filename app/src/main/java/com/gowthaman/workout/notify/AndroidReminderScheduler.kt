package com.gowthaman.workout.notify

import android.content.Context
import com.gowthaman.workout.logic.ReminderScheduler
import com.gowthaman.workout.logic.ReminderSettings

/**
 * [ReminderScheduler] over AlarmManager.
 *
 * A thin adapter: [ReminderAlarm] already does the work, and this only gives :core a way
 * to ask for it without knowing what a Context is.
 */
class AndroidReminderScheduler(context: Context) : ReminderScheduler {

    private val appContext = context.applicationContext

    override fun applyAll(settings: ReminderSettings) =
        ReminderAlarm.applyAll(appContext, settings)

    override fun cancel(id: Int) = ReminderAlarm.cancel(appContext, id)
}
