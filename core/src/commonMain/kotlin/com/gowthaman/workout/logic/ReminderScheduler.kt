package com.gowthaman.workout.logic

/**
 * Arms the nudges the person asked for.
 *
 * The decisions are already shared -- [Reminders.nextTrigger] works out when a reminder
 * next comes due, and [ReminderSettings.active] which ones count. What is left is the act
 * of telling the operating system, and no two systems agree on that: Android has
 * AlarmManager and a notification channel, iOS has UNUserNotificationCenter and its own
 * permission prompt.
 *
 * So this is the whole of what a platform owes the reminder feature, and it is two
 * methods. [applyAll] is deliberately a re-arm of everything rather than a diff: reminders
 * are a handful of items, re-arming is idempotent, and a diff would be one more thing to
 * get wrong in exchange for nothing a person could perceive.
 */
interface ReminderScheduler {

    /**
     * Re-arms every active reminder and cancels the rest.
     *
     * Called after any change, including the one that switches reminders off -- writing
     * the preference without re-arming leaves the phone buzzing on yesterday's schedule.
     */
    fun applyAll(settings: ReminderSettings)

    /**
     * Cancels one reminder's pending alarm.
     *
     * Needed because a deleted reminder's alarm outlives it otherwise, pending and
     * orphaned, with nothing left to look up when it fires.
     */
    fun cancel(id: Int)

    /**
     * A scheduler for a platform that has not got one yet, so :core can be built and
     * tested without one.
     */
    companion object {
        val None: ReminderScheduler = object : ReminderScheduler {
            override fun applyAll(settings: ReminderSettings) = Unit
            override fun cancel(id: Int) = Unit
        }
    }
}
