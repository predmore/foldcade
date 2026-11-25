package app.foldcade

internal data class HostedCardCaller(val uid: Int, val packageName: String)

internal enum class HostedCardClose { Closed, Detached, Failed }

internal data class HostedCardOpen(
    val caller: HostedCardCaller,
    val documentId: String,
    val mode: String,
    val canRead: Boolean,
    val canWrite: Boolean,
    val openedAtMillis: Long,
    val closedAtMillis: Long? = null,
    val closeStatus: HostedCardClose? = null,
    val publishedAs: String? = null,
    val publishedAtMillis: Long? = null,
)

/** The descriptors stay regular files. Their modes show access and a close is only descriptor lifecycle. */
internal class HostedCardOpens(
    private val capacity: Int = 256,
    private val recordingSinceMillis: Long = System.currentTimeMillis(),
) {
    private val opens = linkedMapOf<Long, HostedCardOpen>()
    private var sequence = 0L
    private var lastEvictedAtMillis = Long.MIN_VALUE

    @Synchronized fun opened(open: HostedCardOpen): Long {
        if (opens.size == capacity) {
            val first = opens.entries.first()
            lastEvictedAtMillis = maxOf(lastEvictedAtMillis, first.value.publishedAtMillis ?: first.value.openedAtMillis)
            opens.remove(first.key)
        }
        return (++sequence).also { opens[it] = open }
    }

    @Synchronized fun closed(id: Long, atMillis: Long, status: HostedCardClose) {
        val open = opens[id] ?: return
        opens[id] = open.copy(closedAtMillis = atMillis, closeStatus = status)
    }

    /** DuckStation may write a temporary file then rename it onto the actual card. */
    @Synchronized fun published(caller: HostedCardCaller, from: String, to: String, atMillis: Long) {
        if (from.endsWith(".mcd", ignoreCase = true)) return
        val entry = opens.entries.lastOrNull {
            it.value.let { open -> open.caller == caller && open.documentId == from && open.canWrite && open.publishedAs == null }
        } ?: return
        entry.setValue(entry.value.copy(publishedAs = to, publishedAtMillis = atMillis))
    }

    /** Null when the provider cannot account for the whole window. */
    @Synchronized fun during(start: Long, end: Long): List<HostedCardOpen>? =
        if (start > end || start < recordingSinceMillis || start <= lastEvictedAtMillis) null
        else opens.values.filter { it.openedAtMillis in start..end }
}

internal sealed interface HostedCardVerdict {
    data class Expected(val openedAtMillis: Long) : HostedCardVerdict
    /** Eligible for a game Backup only when a single per-game card can be attributed to this session. */
    data class Different(val names: List<String>, val backupName: String?, val openedAtMillis: Long) : HostedCardVerdict
    data object Unknown : HostedCardVerdict
}

internal fun judgeHostedCard(
    opens: List<HostedCardOpen>,
    caller: HostedCardCaller,
    rootId: String,
    expected: String,
    start: Long,
    end: Long,
    sharedCard1: String? = null,
    possibleSharedCards: Set<String> = setOf(DUCKSTATION_SHARED_CARD_1),
    possibleSlot2Cards: Set<String> = emptySet(),
): HostedCardVerdict {
    val cards = mutableMapOf<String, Long>()
    for (open in opens) {
        if (open.caller != caller || open.openedAtMillis !in start..end) continue
        val published = open.publishedAtMillis?.takeIf { it in start..end }
        val documentId = if (published != null) open.publishedAs ?: continue else open.documentId
        // A native client can detach the descriptor and keep writing. A bare write-capable
        // open cannot show a complete card, even when Android reports a clean close.
        if (published == null && (!open.canRead || open.canWrite)) continue
        if (published != null && open.closeStatus == HostedCardClose.Failed) continue
        if (HostedFolders.parentId(documentId) != rootId) continue
        val name = documentId.substringAfterLast('/')
        // Starting a new DuckStation activity can flush the previous game's card.
        if (name != expected && published != null) continue
        if (name in possibleSlot2Cards) continue
        if (name in possibleSharedCards && name != sharedCard1) continue
        if (name != expected && name != sharedCard1 && !name.endsWith("_1.mcd", ignoreCase = true)) continue
        val at = published ?: open.openedAtMillis
        cards[name] = maxOf(cards[name] ?: at, at)
    }
    if (cards.isEmpty()) return HostedCardVerdict.Unknown
    val others = cards.keys.filter { it != expected }
    if (others.isEmpty()) return HostedCardVerdict.Expected(cards.getValue(expected))
    val backup = others.singleOrNull()?.takeIf { cards.size == 1 && it != sharedCard1 }
    return HostedCardVerdict.Different(others, backup, others.maxOf(cards::getValue))
}
