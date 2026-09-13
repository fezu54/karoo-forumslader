package org.happycode.karoo.forumslader.extension

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

fun Context.bluetoothStateFlow(): Flow<Boolean> = callbackFlow {
    val bluetoothManager = getSystemService<BluetoothManager>()
    val adapter = bluetoothManager?.adapter

    trySend(adapter?.isEnabled == true)

    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                    BluetoothAdapter.STATE_ON -> trySend(true)
                    BluetoothAdapter.STATE_OFF, BluetoothAdapter.STATE_TURNING_OFF -> trySend(false)
                }
            }
        }
    }

    ContextCompat.registerReceiver(
        this@bluetoothStateFlow,
        receiver,
        IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
        ContextCompat.RECEIVER_EXPORTED,
    )

    awaitClose {
        runCatching { unregisterReceiver(receiver) }
    }
}.distinctUntilChanged()
