package com.example.alarmaapp.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.alarmaapp.ble.BleConstants
import com.example.alarmaapp.ble.BleDevice
import com.example.alarmaapp.ble.BleManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

class BleRepository(context: Context, aesKey: String) {
    
    private val bleManager = BleManager(context, aesKey)
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences("AlarmaAppPrefs", Context.MODE_PRIVATE)
    
    companion object {
        private const val KEY_DEVICE_ADDRESS = "device_address"
        private const val KEY_DEVICE_NAME = "device_name"
        private const val KEY_PASSWORD = "password"
    }
    
    val connectionState: StateFlow<BleManager.ConnectionState> = bleManager.connectionState
    val statusAlarm = bleManager.statusAlarm
    val statusMessage: StateFlow<String> = bleManager.statusMessage
    
    fun isBluetoothEnabled(): Boolean {
        return bleManager.isBluetoothEnabled()
    }
    
    fun scanDevices(): Flow<List<BleDevice>> {
        return bleManager.scanDevices()
    }
    
    fun connect(deviceAddress: String) {
        bleManager.connect(deviceAddress)
    }
    
    fun disconnect() {
        bleManager.disconnect()
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
    }
    
    fun getSavedDeviceAddress(): String? {
        return sharedPreferences.getString(KEY_DEVICE_ADDRESS, null)
    }
    
    fun getSavedDeviceName(): String? {
        return sharedPreferences.getString(KEY_DEVICE_NAME, null)
    }
    
    fun getSavedPassword(): String {
        return sharedPreferences.getString(KEY_PASSWORD, BleConstants.DEFAULT_KEY) ?: BleConstants.DEFAULT_KEY
    }
}
