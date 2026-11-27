package org.happycode.karoo.forumslader.extension

import android.util.Log
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.ReleaseBluetooth
import io.hammerhead.karooext.models.RequestBluetooth

/**
 * Serializes Karoo Bluetooth lease acquisition (main-thread connect callback) with
 * cancellation (Binder-thread teardown) so a lease is never leaked or double-released.
 */
internal class KarooBluetoothLease(
    private val karooSystem: KarooSystemService,
    val resourceId: String
) {
    private val lock = Any()
    private var acquired = false
    private var released = false

    /** Connects to [karooSystem] and dispatches [RequestBluetooth] unless already released. */
    fun acquire() {
        karooSystem.connect { connected ->
            synchronized(lock) {
                if (connected && !released) {
                    Log.i(TAG, "Requesting Bluetooth lease: $resourceId")
                    karooSystem.dispatch(RequestBluetooth(resourceId))
                    acquired = true
                }
            }
        }
    }

    /** Releases the Bluetooth lease if it was acquired, and prevents future acquisition. */
    fun release() {
        synchronized(lock) {
            if (released) return
            released = true
            if (acquired) {
                Log.i(TAG, "Releasing Bluetooth lease: $resourceId")
                karooSystem.dispatch(ReleaseBluetooth(resourceId))
            }
        }
    }

    companion object {
        private const val TAG = "FL_BT_LEASE"
    }
}
