package com.example.alarmaapp.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow

class BleManager(private val context: Context) {

    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bleScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner

    private var bluetoothGatt: BluetoothGatt? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _statusMessage = MutableStateFlow<String>("")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    val aes = AES()

    companion object {
        private const val TAG = "BleManager"
    }

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        DISCONNECTING
    }

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    @SuppressLint("MissingPermission")
    fun scanDevices(): Flow<List<BleDevice>> = callbackFlow {
        val scannedDevices = mutableMapOf<String, BleDevice>()

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val deviceName = device.name ?: "Unknown"
                val deviceAddress = device.address

                if (!scannedDevices.containsKey(deviceAddress)) {
                    scannedDevices[deviceAddress] = BleDevice(deviceName, deviceAddress)
                    trySend(scannedDevices.values.toList())
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Scan failed with error: $errorCode")
            }
        }

        // Set up scan filters to look for our service UUID
        val scanFilters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(BleConstants.SERVICE_UUID))
                .build()
        )

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            bleScanner?.startScan(scanFilters, scanSettings, scanCallback)
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing permissions for BLE scan", e)
        }

        awaitClose {
            try {
                bleScanner?.stopScan(scanCallback)
            } catch (e: SecurityException) {
                Log.e(TAG, "Missing permissions to stop scan", e)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(deviceAddress: String, savedPin: String) {
        val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)
        if (device == null) {
            Log.e(TAG, "Device not found")
            return
        }

        _connectionState.value = ConnectionState.CONNECTING

        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        _connectionState.value = ConnectionState.DISCONNECTING
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    @SuppressLint("MissingPermission")
    fun sendCommand(command: String) {
        val gatt = bluetoothGatt
        if (gatt == null) {
            Log.e(TAG, "GATT is null, cannot send command")
            return
        }

        val service = gatt.getService(BleConstants.SERVICE_UUID)
        if (service == null) {
            Log.e(TAG, "Service not found")
            return
        }

        val characteristic = service.getCharacteristic(BleConstants.CHARACTERISTIC_UUID_RX)
        if (characteristic == null) {
            Log.e(TAG, "RX characteristic not found")
            return
        }

        val data = command.toByteArray(Charsets.UTF_8)
        characteristic.value = aes.aesEncrypt(data, aes.secretKeyFromString("abcdefghijklmnop"))

        val success = gatt.writeCharacteristic(characteristic)
        if (!success) {
            Log.e(TAG, "Failed to write characteristic")
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableNotifications(characteristic: BluetoothGattCharacteristic) {
        val gatt = bluetoothGatt ?: return

        gatt.setCharacteristicNotification(characteristic, true)

        val descriptor = characteristic.getDescriptor(BleConstants.CLIENT_CHARACTERISTIC_CONFIG_UUID)
        if (descriptor != null) {
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(TAG, "Connected to GATT server")
                    // Paring
                    if (gatt.device.bondState == BluetoothDevice.BOND_NONE) {
                        gatt.device.createBond() // Start Paring
                    } else if (gatt.device.bondState == BluetoothDevice.BOND_BONDED) {
                        _connectionState.value = ConnectionState.CONNECTED
                        gatt.discoverServices() // Paring Complete
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(TAG, "Disconnected from GATT server")
                    _connectionState.value = ConnectionState.DISCONNECTED
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Services discovered")

                val service = gatt.getService(BleConstants.SERVICE_UUID)
                if (service != null) {
                    val statusCharacteristic = service.getCharacteristic(BleConstants.CHARACTERISTIC_UUID_STATUS)
                    if (statusCharacteristic != null) {
                        enableNotifications(statusCharacteristic)
                    }
                }
            } else {
                Log.w(TAG, "onServicesDiscovered received: $status")
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == BleConstants.CHARACTERISTIC_UUID_STATUS) {
                val value = characteristic.value
//                Decrypt yhe value
                val message = String(aes.aesDecrypt(value, aes.secretKeyFromString("abcdefghijklmnop")), Charsets.UTF_8)
                Log.i(TAG, "Status message received: $message")
                _statusMessage.value = message
            }
        }
    }
}