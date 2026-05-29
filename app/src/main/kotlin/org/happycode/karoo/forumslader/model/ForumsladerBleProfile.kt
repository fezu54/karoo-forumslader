package org.happycode.karoo.forumslader.model

import java.util.UUID

object ForumsladerBleProfile {
    val SERVICE_UUID: UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
    val CHARACTERISTIC_UART_TX_RX: UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
}