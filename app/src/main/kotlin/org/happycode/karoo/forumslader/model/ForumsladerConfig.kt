package org.happycode.karoo.forumslader.model

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.happycode.karoo.forumslader.PreferencesConstants.KEY_LOCKED_MAC_ADDRESS
import org.happycode.karoo.forumslader.PreferencesConstants.KEY_POLES
import org.happycode.karoo.forumslader.PreferencesConstants.KEY_VERSION
import org.happycode.karoo.forumslader.PreferencesConstants.KEY_WHEEL_SIZE
import org.happycode.karoo.forumslader.PreferencesConstants.PREFS_NAME

class ForumsladerConfig(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var wheelsize: Int
        get() = prefs.getInt(KEY_WHEEL_SIZE, 2200)
        set(value) = prefs.edit { putInt(KEY_WHEEL_SIZE, value) }

    var poles: Int
        get() = prefs.getInt(KEY_POLES, 14)
        set(value) = prefs.edit { putInt(KEY_POLES, value) }

    var version: ForumsladerVersion
        get() = ForumsladerVersion.fromKey(prefs.getString(KEY_VERSION, ForumsladerVersion.Unknown.key))
        set(value) = prefs.edit { putString(KEY_VERSION, value.key) }

    var lockedMacAddress: String?
        get() = prefs.getString(KEY_LOCKED_MAC_ADDRESS, null)
        set(value) = prefs.edit { putString(KEY_LOCKED_MAC_ADDRESS, value) }
}
