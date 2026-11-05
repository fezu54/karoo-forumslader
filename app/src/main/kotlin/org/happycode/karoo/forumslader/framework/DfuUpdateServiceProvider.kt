package org.happycode.karoo.forumslader.framework

import android.content.Context
import org.happycode.karoo.forumslader.adapters.ble.NordicDfuAdapter
import org.happycode.karoo.forumslader.domain.DfuUpdateService
import org.happycode.karoo.forumslader.model.ForumsladerConfig

object DfuUpdateServiceProvider {
    @Volatile
    private var instance: DfuUpdateService? = null

    fun getInstance(context: Context): DfuUpdateService =
        instance ?: synchronized(this) {
            instance ?: createService(context.applicationContext).also { instance = it }
        }

    private fun createService(applicationContext: Context) = NordicDfuAdapter(
        context = applicationContext,
        deviceAddressProvider = ForumsladerConfig(applicationContext)::lockedMacAddress
    )

    internal fun reset() {
        synchronized(this) {
            instance = null
        }
    }
}
