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
    
    // Comandos (viajan cifrados con AES-CBC)
    const val COMMAND_TOGGLE = "1"          // Bloquear / desbloquear
    const val COMMAND_POWER_TOGGLE = "2"    // Encender / apagar GPIO 14
    const val COMMAND_STARTER_DOWN = "3"    // Arranque presionado (GPIO 15)
    const val COMMAND_STARTER_UP = "3u"     // Arranque liberado
    const val COMMAND_BUTTON1_DOWN = "4"    // Boton 1 presionado (GPIO 15)
    const val COMMAND_BUTTON1_UP = "4u"     // Boton 1 liberado
    const val COMMAND_BUTTON2_DOWN = "5"    // Boton 2 presionado (GPIO 12)
    const val COMMAND_BUTTON2_UP = "5u"     // Boton 2 liberado

    // Mientras un boton momentaneo esta presionado, la app reenvia el comando
    // "down" cada este intervalo. El firmware lo usa como keep-alive y libera
    // la salida si deja de recibirlo (HOLD_TIMEOUT_MS en board_config.h).
    const val HOLD_REFRESH_MS = 500L

    // Status messages
    const val STATUS_LOCKED = "bloqueada."
    const val STATUS_UNLOCKED = "desbloqueada."
}
