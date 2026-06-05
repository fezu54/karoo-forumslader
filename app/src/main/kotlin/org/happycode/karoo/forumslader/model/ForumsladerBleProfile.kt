package org.happycode.karoo.forumslader.model

import java.util.UUID

object ForumsladerBleProfile {
    val SERVICE_UUID_V5: UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
    val SERVICE_UUID_V6: UUID = UUID.fromString("6e40ffe2-b5a3-f393-e0a9-e50e24dcca9e")
    val CHARACTERISTIC_UART_TX_RX: UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
    val CHARACTERISTIC_UART_RX_V6: UUID = UUID.fromString("6e40ef38-b5a3-f393-e0a9-e50e24dcca9e")
    val CHARACTERISTIC_UART_TX_V6: UUID = UUID.fromString("6e40ef39-b5a3-f393-e0a9-e50e24dcca9e")
    val CLIENT_CHARACTERISTIC_CONFIGURATION_DESCRIPTOR: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}