package app.foldcade.host.play

/**
 * One move from [android.app.usage.UsageStatsManager.queryEvents].
 * Wall timestamps, because that is what the usage log stores.
 */
enum class UsageMove {
    Resumed,
    Paused,
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
 */
fun usageSpans(samples: List<UsageSample>): List<UsageSpan> {
    val open = HashMap<String, Long>()
    val spans = mutableListOf<UsageSpan>()
    val ordered = samples.filter { it.packageName.isNotBlank() }.sortedBy { it.atMillis }
    for (sample in ordered) {
        when (sample.move) {
            UsageMove.Resumed -> if (sample.packageName !in open) {
                open[sample.packageName] = sample.atMillis
            }
            UsageMove.Paused -> {
                val started = open.remove(sample.packageName) ?: continue
                spans += UsageSpan(sample.packageName, started, sample.atMillis)
            }
        }
    }
    for ((packageName, started) in open) {
        spans += UsageSpan(packageName, started, null)
    }
    return spans
}

/** Active time per package. A negative gap counts as zero. An open stretch runs until [nowMillis]. */
fun packagePlay(spans: List<UsageSpan>, nowMillis: Long): Map<String, PackagePlay> {
    val active = HashMap<String, Long>()
    val last = HashMap<String, Long>()
    for (span in spans) {
        val end = span.pausedAt ?: nowMillis
        val delta = (end - span.resumedAt).coerceAtLeast(0L)
        active[span.packageName] = (active[span.packageName] ?: 0L) + delta
        val stamp = if (span.pausedAt != null) span.pausedAt else nowMillis
        val known = last[span.packageName]
        if (known == null || stamp >= known) last[span.packageName] = stamp
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
