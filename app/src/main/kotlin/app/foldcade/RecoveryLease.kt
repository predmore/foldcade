package app.foldcade

/** One window and one delayed repair per return/wake, cancelled when its panel is yielded. */
internal class RecoveryLease(
    private val show: () -> Boolean,
    private val dismiss: () -> Unit,
    private val later: (Long, () -> Unit) -> Unit,
) {
    private var generation = 0
    private var wanted = false
    var active = false
        private set

    fun update(wanted: Boolean, reclaim: Boolean = false) {
        val changed = this.wanted != wanted
        this.wanted = wanted
        if (!wanted) {
            if (changed || active) {
                generation++
                active = false
                dismiss()
            }
            return
        }
        if (!changed && !reclaim) return
        val ticket = ++generation
        active = show()
        if (!active) return
        // Display listeners in the emulator may run after Home or wake has brought us back.
        // Repair once after that transition; never keep competing with another app's window.
        later(1_000) {
            if (this.wanted && generation == ticket) active = show()
        }
    }
}

/** An app opened elsewhere may have its own second-screen window. Yield conservatively. */
internal fun canRecoverPanel(session: Session, panel: Panel): Boolean =
    session.surfaceOn(panel) != null && session.externalOn(panel.flip())?.id != OTHER_APP_ID
