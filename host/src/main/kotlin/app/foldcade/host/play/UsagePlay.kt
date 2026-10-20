package app.foldcade.host.play

/**
 * One move from [android.app.usage.UsageStatsManager.queryEvents].
 * Wall timestamps, because that is what the usage log stores.
 * [ScreenOff] and [ScreenOn] are the device's, not a package's.
 */
enum class UsageMove {
    Resumed,
    Paused,
    ScreenOff,
    ScreenOn,
}

data class UsageSample(
    val packageName: String,
    val move: UsageMove,
    val atMillis: Long,
)

/**
 * One foreground stretch of a package.
 * [pausedAt] is null while that package is still resumed.
 */
data class UsageSpan(
    val packageName: String,
    val resumedAt: Long,
    val pausedAt: Long?,
)

data class PackagePlay(
    val packageName: String,
    val activeMillis: Long,
    val lastPlayedMillis: Long?,
)

data class ShownPlay(
    val activeMillis: Long,
    val lastPlayedMillis: Long?,
    val approximate: Boolean,
)

/**
 * Turns resumed and paused events into foreground stretches.
 * A second resume while one is open does not restart it.
 * A pause with nothing open is ignored. Packages stay separate,
 * so a game played from Recents or another launcher is still a stretch.
 * The screen turning off, as it does with the lid closed, ends every open
 * stretch. Each package still in front when it turns back on starts a new one.
 */
fun usageSpans(samples: List<UsageSample>): List<UsageSpan> {
    val open = HashMap<String, Long>()
    val asleep = HashSet<String>()
    var screenOn = true
    val spans = mutableListOf<UsageSpan>()
    val ordered = samples
        .filter { it.move == UsageMove.ScreenOff || it.move == UsageMove.ScreenOn || it.packageName.isNotBlank() }
        .sortedBy { it.atMillis }
    for (sample in ordered) {
        when (sample.move) {
            UsageMove.Resumed -> when {
                !screenOn -> asleep += sample.packageName
                sample.packageName !in open -> open[sample.packageName] = sample.atMillis
            }
            UsageMove.Paused -> {
                if (!screenOn) {
                    asleep -= sample.packageName
                    continue
                }
                val started = open.remove(sample.packageName) ?: continue
                spans += UsageSpan(sample.packageName, started, sample.atMillis)
            }
            UsageMove.ScreenOff -> if (screenOn) {
                screenOn = false
                for ((packageName, started) in open) {
                    spans += UsageSpan(packageName, started, sample.atMillis)
                }
                asleep += open.keys
                open.clear()
            }
            UsageMove.ScreenOn -> if (!screenOn) {
                screenOn = true
                for (packageName in asleep) open[packageName] = sample.atMillis
                asleep.clear()
            }
        }
    }
    for ((packageName, started) in open) {
        spans += UsageSpan(packageName, started, null)
    }
    return spans
}

/**
 * Active time per package. A negative gap counts as zero. An open stretch runs until [nowMillis].
 * A stamp ahead of [nowMillis] is a clock that jumped forward and was corrected: it does not
 * add time past now, and it does not stay as "last played" after the clock comes back.
 */
fun packagePlay(spans: List<UsageSpan>, nowMillis: Long): Map<String, PackagePlay> {
    val active = HashMap<String, Long>()
    val last = HashMap<String, Long>()
    for (span in spans) {
        val rawEnd = span.pausedAt ?: nowMillis
        val end = rawEnd.coerceAtMost(nowMillis)
        val start = span.resumedAt.coerceAtMost(end)
        val delta = (end - start).coerceAtLeast(0L)
        active[span.packageName] = (active[span.packageName] ?: 0L) + delta
        val stamp = when {
            span.pausedAt == null -> nowMillis
            span.pausedAt <= nowMillis -> span.pausedAt
            else -> null
        }
        if (stamp != null) {
            val known = last[span.packageName]
            if (known == null || stamp >= known) last[span.packageName] = stamp
        }
    }
    return active.keys.associateWith { packageName ->
        PackagePlay(packageName, active[packageName] ?: 0L, last[packageName])
    }
}

/**
 * Usage access replaces lifecycle time for a package we have events for.
 * Without access, the lifecycle total stays and is marked approximate.
 */
fun shownPlay(
    lifecycleActive: Long,
    lifecycleLast: Long?,
    usage: PackagePlay?,
    granted: Boolean,
): ShownPlay {
    if (granted && usage != null) {
        return ShownPlay(
            activeMillis = usage.activeMillis,
            lastPlayedMillis = usage.lastPlayedMillis ?: lifecycleLast,
            approximate = false,
        )
    }
    return ShownPlay(lifecycleActive, lifecycleLast, approximate = !granted)
}
