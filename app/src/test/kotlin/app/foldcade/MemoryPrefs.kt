package app.foldcade

import android.content.SharedPreferences

/** In-memory prefs for app unit tests. */
internal class MemoryPrefs : SharedPreferences {
    private val values = HashMap<String, Any>()

    override fun getAll(): MutableMap<String, *> = HashMap(values)

    override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
        val stored = values[key] as? Set<*> ?: return defValues
        return stored.mapNotNull { it as? String }.toMutableSet()
    }

    override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue

    override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue

    override fun contains(key: String?): Boolean = key != null && values.containsKey(key)

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val pending = HashMap<String, Any?>()
        private var clearAll = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor = put(key, value)

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor =
            put(key, values?.toSet())

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = put(key, value)

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = put(key, value)

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = put(key, value)

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = put(key, value)

        override fun remove(key: String?): SharedPreferences.Editor = put(key, null)

        override fun clear(): SharedPreferences.Editor {
            clearAll = true
            pending.clear()
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clearAll) values.clear()
            pending.forEach { (key, value) ->
                if (value == null) values.remove(key) else values[key] = value
            }
        }

        private fun put(key: String?, value: Any?): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }
    }

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit
}
