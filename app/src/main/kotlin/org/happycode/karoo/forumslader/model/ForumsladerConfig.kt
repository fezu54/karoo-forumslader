package org.happycode.karoo.forumslader.model

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class ForumsladerConfig(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("forumslader_prefs", Context.MODE_PRIVATE)

    var wheelsize: Int
        get() = prefs.getInt(KEY_WHEELSIZE, 2200)
        set(value) = prefs.edit { putInt(KEY_WHEELSIZE, value) }

    var poles: Int
        get() = prefs.getInt(KEY_POLES, 14)
        set(value) = prefs.edit { putInt(KEY_POLES, value) }

    var version: ForumsladerVersion
        get() = ForumsladerVersion.fromKey(prefs.getString(KEY_VERSION, ForumsladerVersion.Unknown.key))
        set(value) = prefs.edit { putString(KEY_VERSION, value.key) }

    var speedMultiplier: Float
        get() = prefs.getFloat(KEY_SPEED_MULTIPLIER, 1.0f)
        set(value) = prefs.edit { putFloat(KEY_SPEED_MULTIPLIER, value) }

    companion object {
        private const val KEY_WHEELSIZE = "wheelsize"
        private const val KEY_POLES = "poles"
        private const val KEY_VERSION = "version"
        private const val KEY_SPEED_MULTIPLIER = "speedMultiplier"
    }
}
