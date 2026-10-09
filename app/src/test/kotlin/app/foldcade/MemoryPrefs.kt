package app.foldcade

import android.content.SharedPreferences

/** In-memory prefs for app unit tests. Writes are accepted and discarded. */
internal class MemoryPrefs : SharedPreferences {
    override fun getAll(): MutableMap<String, *> = mutableMapOf<String, Any>()

    override fun getString(key: String?, defValue: String?): String? = defValue

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues

    override fun getInt(key: String?, defValue: Int): Int = defValue

    override fun getLong(key: String?, defValue: Long): Long = defValue

    override fun getFloat(key: String?, defValue: Float): Float = defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean = defValue

    override fun contains(key: String?): Boolean = false

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        override fun putString(key: String?, value: String?) = this
        override fun putStringSet(key: String?, values: MutableSet<String>?) = this
        override fun putInt(key: String?, value: Int) = this
        override fun putLong(key: String?, value: Long) = this
        override fun putFloat(key: String?, value: Float) = this
        override fun putBoolean(key: String?, value: Boolean) = this
        override fun remove(key: String?) = this
        override fun clear() = this
        override fun commit(): Boolean = true
        override fun apply() = Unit
    }

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit
}
