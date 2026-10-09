package app.foldcade

import android.app.Application

class FoldcadeApp : Application() {
    lateinit var store: SessionStore
        private set
    lateinit var shell: ShellController
        private set
    var companionLaunched: Boolean = false

    override fun onCreate() {
        super.onCreate()
        store = SessionStore(getSharedPreferences("foldcade", MODE_PRIVATE))
        shell = ShellController(store)
    }
}
