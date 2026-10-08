@file:Suppress("unused", "UNUSED_PARAMETER")
package android.content
interface SharedPreferences {
    interface Editor { fun putString(k: String, v: String?): Editor; fun putInt(k: String, v: Int): Editor; fun putBoolean(k: String, v: Boolean): Editor; fun apply() }
    fun getString(k: String, d: String?): String?; fun getInt(k: String, d: Int): Int; fun getBoolean(k: String, d: Boolean): Boolean; fun edit(): Editor
}
abstract class Context { abstract fun getSharedPreferences(n: String, m: Int): SharedPreferences; companion object { const val MODE_PRIVATE = 0 } }
