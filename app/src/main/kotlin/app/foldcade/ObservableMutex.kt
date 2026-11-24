package app.foldcade

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.sync.Mutex

/** A save or backup ending wakes the update gate, even when it changes no visible row. */
internal class ObservableMutex(private val mutex: Mutex = Mutex()) : Mutex by mutex {
    private var held by mutableStateOf(false)
    override val isLocked: Boolean get() { val observed = held; return observed || mutex.isLocked }
    override suspend fun lock(owner: Any?) { mutex.lock(owner); held = true }
    override fun tryLock(owner: Any?): Boolean = mutex.tryLock(owner).also { if (it) held = true }
    override fun unlock(owner: Any?) { mutex.unlock(owner); held = mutex.isLocked }
}
