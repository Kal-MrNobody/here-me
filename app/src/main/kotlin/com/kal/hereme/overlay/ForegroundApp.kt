package com.kal.hereme.overlay

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

/**
 * Which app is in front right now.
 *
 * Uses UsageStatsManager rather than an AccessibilityService: it doesn't read screen
 * content or input, so it stays clear of the screen-jacking pattern payment apps refuse
 * to run alongside. It needs the "usage access" special permission, and only works once
 * that is granted.
 */
object ForegroundApp {

    /** The package of the most recent app to come to the foreground, or null if unknown. */
    fun current(context: Context): String? {
        val usage = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val now = System.currentTimeMillis()

        // A short look-back window is enough to catch the last foreground switch; a longer
        // one just means more events to scan for the same answer.
        val events = usage.queryEvents(now - LOOKBACK_MILLIS, now)
        val event = UsageEvents.Event()
        var latest: String? = null

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
            ) {
                latest = event.packageName
            }
        }
        return latest
    }

    private const val LOOKBACK_MILLIS = 60_000L
}
