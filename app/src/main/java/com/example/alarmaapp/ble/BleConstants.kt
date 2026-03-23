package com.example.alarmaapp.ble

import java.util.UUID

object BleConstants {
    // Service and Characteristic UUIDs
    val SERVICE_UUID: UUID = UUID.fromString("e6067851-5971-4b21-a8cc-17738c56ea49")
    val CHARACTERISTIC_UUID_STATUS: UUID = UUID.fromString("db9ab4aa-da20-4de8-8a08-e14ab7e5148e")
    val CHARACTERISTIC_UUID_RX: UUID = UUID.fromString("f0bf0a71-0dfa-4d1b-90ae-cfda669a37c0")
    val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    
    // Default PIN
    const val DEFAULT_KEY = "abcdefghijklmnop"
    
    // Scan timeout
    const val SCAN_TIMEOUT_MS = 10000L
    
    // Command to send
    const val COMMAND_TOGGLE = "1"
    
    // Status messages
    const val STATUS_LOCKED = "bloqueada."
    const val STATUS_UNLOCKED = "desbloqueada."
}
