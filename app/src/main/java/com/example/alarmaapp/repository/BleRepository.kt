package com.example.alarmaapp.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.alarmaapp.ble.BleConstants
import com.example.alarmaapp.ble.BleDevice
import com.example.alarmaapp.ble.BleManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

class BleRepository(context: Context, private val defaultAesKey: String) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("AlarmaAppPrefs", Context.MODE_PRIVATE)

    // OJO: antes se construia siempre con la clave por defecto, sin leer la
    // guardada, asi que cambiar la contrasena en Ajustes no tenia efecto.
    private val bleManager = BleManager(
        context,
        normalizeKey(sharedPreferences.getString(KEY_PASSWORD, null))
    )

    // Una clave guardada de una version anterior puede tener longitud invalida
    // (p. ej. "123456"). En ese caso usamos la clave por defecto para que la
    // app siga funcionando con el firmware.
    private fun normalizeKey(key: String?): String {
        val candidate = key?.trim().orEmpty()
        return if (candidate.length == 16 || candidate.length == 24 || candidate.length == 32) {
            candidate
        } else {
            defaultAesKey
        }
    }
    
    companion object {
        private const val KEY_DEVICE_ADDRESS = "device_address"
        private const val KEY_DEVICE_NAME = "device_name"
        private const val KEY_PASSWORD = "password"
    }
    
    val connectionState: StateFlow<BleManager.ConnectionState> = bleManager.connectionState
    val statusAlarm: StateFlow<BleManager.StatusAlarm> = bleManager.statusAlarm
    
    fun isBluetoothEnabled(): Boolean {
        return bleManager.isBluetoothEnabled()
    }
    
    fun scanDevices(): Flow<List<BleDevice>> {
        return bleManager.scanDevices()
    }
    
    fun connect(deviceAddress: String) {
        bleManager.connect(deviceAddress)
    }

    // Usado por el widget: conecta al dispositivo ya guardado en Ajustes.
    fun connectToSavedDevice(): Boolean {
        val state = connectionState.value
        if (state == BleManager.ConnectionState.CONNECTED ||
            state == BleManager.ConnectionState.CONNECTING ||
            state == BleManager.ConnectionState.DISCONNECTING
        ) {
            return false
        }

        val address = getSavedDeviceAddress() ?: return false
        bleManager.connect(address)
        return true
    }

    fun hasSavedDevice(): Boolean {
        return getSavedDeviceAddress() != null
    }

    fun disconnect() {
        bleManager.disconnect()
    }
    
    fun sendCommand(command: String) {
        bleManager.sendCommand(command)
    }

    fun sendToggleCommand() {
        bleManager.sendCommand(BleConstants.COMMAND_TOGGLE)
    }
    
    fun saveDeviceConfig(deviceAddress: String, deviceName: String, password: String) {
        sharedPreferences.edit().apply {
            putString(KEY_DEVICE_ADDRESS, deviceAddress)
            putString(KEY_DEVICE_NAME, deviceName)
            putString(KEY_PASSWORD, password)
            apply()
        }
        bleManager.updateAesKey(password)
    }
    
    fun getSavedDeviceAddress(): String? {
        return sharedPreferences.getString(KEY_DEVICE_ADDRESS, null)
    }
    
    fun getSavedDeviceName(): String? {
        return sharedPreferences.getString(KEY_DEVICE_NAME, null)
    }
    
    fun getSavedPassword(): String {
        return normalizeKey(sharedPreferences.getString(KEY_PASSWORD, null))
    }
}
