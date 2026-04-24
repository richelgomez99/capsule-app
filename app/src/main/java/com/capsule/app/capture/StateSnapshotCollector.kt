package com.capsule.app.capture

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.capsule.app.data.ipc.StateSnapshotParcel
import com.capsule.app.data.model.ActivityState
import com.capsule.app.data.model.AppCategory
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicReference

/**
 * T037 — collects the three at-capture state signals required by
 * research.md §State Signal Collection:
 *
 * 1. **Foreground app category** — via [UsageStatsManager.queryEvents] for
 *    the last 15 seconds; the last `MOVE_TO_FOREGROUND` event's package name
 *    is resolved against [AppCategoryDictionary] and then **discarded**.
 *    Raw package names are never persisted.
 * 2. **Activity Recognition state** — this process holds the last observed
 *    state in a thread-safe [AtomicReference] updated by an
 *    `ActivityTransitionReceiver` (wired separately, not part of this class).
 *    Default when no signal yet seen is [ActivityState.UNKNOWN].
 * 3. **Local time fields** — `tzId`, `hourLocal`, `dayOfWeekLocal` derived
 *    from the device clock + default zone.
 *
 * ### Failure modes (graceful degrade)
 *
 * | Condition | Result |
 * |---|---|
 * | `PACKAGE_USAGE_STATS` not granted (SecurityException or null manager) | `appCategory = UNKNOWN_SOURCE` |
 * | No foreground event in last 15s | `appCategory = UNKNOWN_SOURCE` |
 * | `ACTIVITY_RECOGNITION` not granted / no sample yet | `activityState = UNKNOWN` |
 *
 * Capture never fails because a signal is missing.
 */
class StateSnapshotCollector(
    private val packageResolver: PackageResolver,
    private val activityStateSource: ActivityStateSource,
    private val clock: Clock = Clock { System.currentTimeMillis() },
    private val zoneProvider: ZoneProvider = ZoneProvider { ZoneId.systemDefault() }
) {

    /**
     * Snapshot the current state. Safe to call from any thread.
     */
    fun snapshot(): StateSnapshotParcel {
        val now = clock.nowMillis()
        val zone = zoneProvider.zone()
        val local = Instant.ofEpochMilli(now).atZone(zone)

        val category = packageResolver.resolveForegroundCategory(
            windowStartMillis = now - FOREGROUND_LOOKBACK_MS,
            windowEndMillis = now
        )
        val activity = activityStateSource.current()

        return StateSnapshotParcel(
            appCategory = category.name,
            activityState = activity.name,
            tzId = zone.id,
            hourLocal = local.hour,
            dayOfWeekLocal = local.dayOfWeek.value // 1 = Monday .. 7 = Sunday
        )
    }

    /** Abstraction over [UsageStatsManager] so the collector is JVM-testable. */
    fun interface PackageResolver {
        fun resolveForegroundCategory(windowStartMillis: Long, windowEndMillis: Long): AppCategory
    }

    /** Abstraction over the process-local Activity Recognition cache. */
    fun interface ActivityStateSource {
        fun current(): ActivityState
    }

    fun interface Clock {
        fun nowMillis(): Long
    }

    fun interface ZoneProvider {
        fun zone(): ZoneId
    }

    companion object {
        /** Events older than this are ignored when resolving foreground app. */
        const val FOREGROUND_LOOKBACK_MS: Long = 15_000L

        /**
         * Production factory — wires a real [UsageStatsManager]-backed
         * [PackageResolver] and the process-local [ActivityStateCache]
         * singleton. Callers on the `:capture` process use this; tests
         * instantiate the class directly with fakes.
         */
        fun create(context: Context): StateSnapshotCollector {
            val appContext = context.applicationContext
            val usageStats = appContext.getSystemService(Context.USAGE_STATS_SERVICE)
                as? UsageStatsManager
            val resolver = PackageResolver { start, end ->
                resolveFromUsageStats(usageStats, start, end)
            }
            return StateSnapshotCollector(
                packageResolver = resolver,
                activityStateSource = { ActivityStateCache.current() }
            )
        }

        private fun resolveFromUsageStats(
            usageStats: UsageStatsManager?,
            windowStartMillis: Long,
            windowEndMillis: Long
        ): AppCategory {
            if (usageStats == null) return AppCategory.UNKNOWN_SOURCE
            val events = try {
                usageStats.queryEvents(windowStartMillis, windowEndMillis)
            } catch (_: SecurityException) {
                return AppCategory.UNKNOWN_SOURCE
            }
            var latestTs = Long.MIN_VALUE
            var latestPkg: String? = null
            val buf = UsageEvents.Event()
            while (events.hasNextEvent()) {
                if (!events.getNextEvent(buf)) break
                if (buf.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND &&
                    buf.timeStamp > latestTs
                ) {
                    latestTs = buf.timeStamp
                    latestPkg = buf.packageName
                }
            }
            return AppCategoryDictionary.categorize(latestPkg)
        }
    }
}

/**
 * Process-local cache of the most recent Activity Recognition transition.
 * Updated by an `ActivityTransitionReceiver` (wired in a later slice);
 * read by [StateSnapshotCollector] at capture time. Defaults to
 * [ActivityState.UNKNOWN] until the first transition is observed.
 */
object ActivityStateCache {
    private val state = AtomicReference(ActivityState.UNKNOWN)

    fun current(): ActivityState = state.get()

    fun update(newState: ActivityState) {
        state.set(newState)
    }
}
