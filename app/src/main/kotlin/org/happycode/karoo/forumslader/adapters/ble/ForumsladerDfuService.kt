package org.happycode.karoo.forumslader.adapters.ble

import android.app.Activity
import no.nordicsemi.android.dfu.DfuBaseService
import org.happycode.karoo.forumslader.MainActivity

open class ForumsladerDfuService : DfuBaseService() {
    override fun getNotificationTarget(): Class<out Activity> = MainActivity::class.java

    override fun isDebug(): Boolean = false
}
