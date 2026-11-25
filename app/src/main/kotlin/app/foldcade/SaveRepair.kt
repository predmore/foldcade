package app.foldcade

import app.foldcade.api.plugin.PluginException
import app.foldcade.language.IncidentKind
import app.foldcade.language.RepairState
import app.foldcade.language.SaveConflictAction
import app.foldcade.language.SaveIncident
import app.foldcade.plugins.romm.RommRepairHeadChanged
import app.foldcade.plugins.romm.RommRepairNeedsSync
import app.foldcade.plugins.romm.RommRepairSave
import kotlinx.coroutines.delay
import kotlin.coroutines.cancellation.CancellationException

internal data class RepairCopies(
    val bound: String?, val live: String?, val besideGame: String?, val suspect: Boolean = false, val liveMatchesSynced: Boolean = false,
) {
    fun hashes() = setOfNotNull(bound, live, besideGame)
    fun sameBytes(other: RepairCopies) = bound == other.bound && live == other.live && besideGame == other.besideGame
}

internal class RepairFoldersUnavailable : Exception()
internal class RepairGameRunning : Exception()

/** Each call is serialized with launch preparation, read-back, and other save placements. */
internal interface SaveRepairWork {
    val slot: String
    suspend fun running(): Boolean
    suspend fun versions(): List<RommRepairSave>
    suspend fun readCopies(keptHashes: Set<String>, stage: Boolean): RepairCopies
    suspend fun uploadBackups(): Boolean
    suspend fun bind()
    suspend fun place(selection: RepairState.Placing, copies: RepairCopies)
    suspend fun verify()
}

/** Drives one durable incident. A phase is recorded before any binding or placement. */
internal class SaveRepair(
    private val store: SaveProtectionStore,
    private val workFor: (SaveIncident) -> SaveRepairWork?,
    private val settle: suspend () -> Unit = { delay(2_000) },
    private val changed: suspend () -> Unit = {},
) {
    suspend fun advance(id: String, start: Boolean = false, choice: Pair<RepairState.Choosing, SaveConflictAction>? = null): SaveIncident? {
        val incident = store.incident(id) ?: return null
        val state = incident.repair
        if (state == RepairState.Done) {
            if (incident.automaticDone) store.close(id)
            return incident.takeIf { it.automaticDone }
        }
        if (state == RepairState.Binding || state == RepairState.Guided || state == RepairState.NotStarted && !start) return null
        if (state is RepairState.Choosing && choice?.first != state && !(start && choice == null)) return null
        if (choice?.second == SaveConflictAction.DecideLater) return null

        suspend fun update(next: RepairState) {
            val latest = store.incident(id) ?: return
            if (latest.repair != next) {
                store.put(latest.copy(repair = next, recoveryRequired = true))
                changed()
            }
        }

        val quarantine = incident.kind == IncidentKind.MaybeNewSave
        var backupsKept = (state as? RepairState.WaitingForClose)?.backupsKept == true || state is RepairState.Placing
        var selection = when (state) {
            is RepairState.Placing -> state
            is RepairState.WaitingForClose -> state.resume
            is RepairState.WaitingForRomm -> state.resume
            else -> null
        }
        try {
            val work = workFor(incident) ?: throw RepairFoldersUnavailable()
            if (work.running()) throw RepairGameRunning()
            if (state == RepairState.NotStarted) update(RepairState.Keeping)
            val listed = try { work.versions() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (unsupported: RommRepairNeedsSync) { throw unsupported }
            catch (_: Exception) {
                if (!quarantine) work.readCopies(emptySet(), stage = true)
                update(RepairState.WaitingForRomm(selection))
                return null
            }
            val backups = listed.filter { it.slot == null }.associateBy { it.hash }
            val alreadyPlaced = selection?.let { plan -> listed.firstOrNull { it.id == plan.saveId }?.hash }
            val working = work.readCopies(backups.keys + listOfNotNull(alreadyPlaced), stage = true)
            if (!work.uploadBackups()) {
                update(RepairState.WaitingForRomm(selection))
                return null
            }
            val needed = working.hashes().filter { it !in backups && it != alreadyPlaced }
            val verified = if (needed.isEmpty()) listed else work.versions()
            val kept = verified.filter { it.slot == null }.associateBy { it.hash }
            if (needed.any { it !in kept }) {
                update(RepairState.WaitingForRomm(selection))
                return null
            }
            backupsKept = true
            val keptIds = working.hashes().mapNotNull { kept[it]?.id }
            store.incident(id)?.let { latest ->
                val all = (latest.kept + keptIds).distinct()
                if (all != latest.kept) store.put(latest.copy(kept = all))
            }
            val head = verified.firstOrNull { it.slot == work.slot }?.takeIf { it.hash.isNotEmpty() } ?: throw RommRepairNeedsSync()
            val liveHash = if (quarantine) incident.quarantinedHash else working.live
            val live = kept[liveHash]
            val suspect = quarantine || working.suspect
            val next = if (live != null && live.hash != head.hash && (quarantine || !suspect && !working.liveMatchesSynced)) {
                RepairState.Choosing(head.id, live.id, head.hash, suspect)
            } else null

            // A restart of an approved placement accepts either the original bytes or the chosen
            // bytes already written. Any subsequent save deserves a fresh decision.
            if (selection != null) {
                val chosenHash = verified.firstOrNull { it.id == selection.saveId }?.hash
                if (chosenHash == null || head.hash !in setOf(selection.headHash, chosenHash) ||
                    working.live !in setOf(selection.liveHash, chosenHash)) selection = null
            }
            if (selection == null && choice != null && next != choice.first) {
                update(next ?: RepairState.WaitingForRomm())
                return null
            }
            if (quarantine && liveHash != head.hash && live == null) {
                update(RepairState.WaitingForRomm(selection))
                return null
            }
            if (quarantine && selection == null && (choice?.second == SaveConflictAction.KeepBoth || liveHash == head.hash)) {
                if (working.live != null && working.live != head.hash) {
                    val subject = incident.subject
                    if (subject != null) store.requireBackup(incident.playerKey, subject.game())
                }
                return complete(id)
            }

            settle()
            if (work.running() || !work.readCopies(emptySet(), stage = false).sameBytes(working)) throw RepairGameRunning()
            if (!quarantine) {
                if (selection == null) update(RepairState.Rebinding)
                work.bind()
            }
            if (selection == null && next != null && choice == null) {
                update(next)
                return null
            }
            val selected = selection ?: RepairState.Placing(
                if (choice?.second == SaveConflictAction.KeepLocal) next!!.backupId else head.id,
                head.hash, working.live,
            )
            selection = selected
            update(selected)
            work.place(selected, working)
            work.verify()
            return complete(id)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: RepairGameRunning) {
            update(RepairState.WaitingForClose(selection, backupsKept))
        } catch (_: RommRepairHeadChanged) {
            // Read and preserve again on the next Home refresh before proposing a new head.
            update(RepairState.WaitingForRomm())
        } catch (_: RepairFoldersUnavailable) {
            update(if (quarantine) RepairState.WaitingForClose(selection, backupsKept) else RepairState.Guided)
        } catch (_: SaveNotPlaced) {
            update(if (quarantine) RepairState.WaitingForClose(selection, backupsKept) else RepairState.Guided)
        } catch (_: RommRepairNeedsSync) {
            update(if (quarantine) RepairState.WaitingForRomm(selection) else RepairState.Guided)
        } catch (_: PluginException.SaveFormatMismatch) {
            update(if (quarantine && state is RepairState.Choosing) state else RepairState.Guided)
        } catch (_: Exception) {
            update(RepairState.WaitingForRomm(selection))
        }
        return null
    }

    private suspend fun complete(id: String): SaveIncident? {
        val done = store.incident(id)?.copy(repair = RepairState.Done, recoveryRequired = false, automaticDone = true) ?: return null
        store.put(done)
        changed()
        store.close(id)
        return done
    }
}

internal fun repairFolderMatches(expected: String?, actual: String) = expected == null || expected == actual
