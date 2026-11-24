package com.example.alarmaapp.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
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
import kotlinx.coroutines.flow.update

class BleManager(private val context: Context, aesKey: String) {

    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bleScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner

    private var bluetoothGatt: BluetoothGatt? = null

    // Android solo permite una operacion GATT a la vez. Encolamos los
    // comandos para que un "up" (soltar) no se pierda si justo se estaba
    // escribiendo el "down" anterior.
    private val commandLock = Any()
    private val commandQueue = ArrayDeque<ByteArray>()
    private var writeInProgress = false

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _statusAlarm = MutableStateFlow(StatusAlarm())
    val statusAlarm: StateFlow<StatusAlarm> = _statusAlarm.asStateFlow()

    @Volatile
    private var aes = AES(aesKey)

    // Permite cambiar la clave sin reiniciar la app (Ajustes -> Guardar).
    fun updateAesKey(key: String) {
        aes = AES(key)
    }

    companion object {
        private const val TAG = "BleManager"
    }

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        DISCONNECTING
    }

    /**
     * Estado que envia la placa:
     * [conectado, bloqueado, encendido, arranque, boton1, boton2]
     *
     * Se mantiene compatibilidad con el firmware anterior, que enviaba
     * "bloqueado,notificado" en los dos primeros campos.
     */
    data class StatusAlarm(
        val connected: Boolean = false,
        val locked: Boolean = true,
        val powerOn: Boolean = false,
        val starterActive: Boolean = false,
        val button1Active: Boolean = false,
        val button2Active: Boolean = false
    ) {
        companion object {
            fun parseStatus(decrypted: String): StatusAlarm? {
                val parts = decrypted.trim().split(",").map { it.trim() }
                if (parts.isEmpty() || parts[0].isEmpty()) {
                    return null
                }

                fun flag(index: Int): Boolean = parts.getOrNull(index) == "1"

                return if (parts.size >= 6) {
                    StatusAlarm(
                        connected = flag(0),
                        locked = flag(1),
                        powerOn = flag(2),
                        starterActive = flag(3),
                        button1Active = flag(4),
                        button2Active = flag(5)
                    )
                } else {
                    // Firmware anterior: parts[0] = bloqueado.
                    StatusAlarm(connected = true, locked = flag(0))
                }
            }
        }
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
    fun connect(deviceAddress: String) {
        val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)
        if (device == null) {
            Log.e(TAG, "Device not found")
            return
        }

        _connectionState.value = ConnectionState.CONNECTING

        // No arrastrar comandos de una conexion anterior.
        synchronized(commandLock) {
            commandQueue.clear()
            writeInProgress = false
        }

        // Si quedo un GATT de un intento anterior, cerrarlo antes de reconectar.
        bluetoothGatt?.close()
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        _connectionState.value = ConnectionState.DISCONNECTING
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        _connectionState.value = ConnectionState.DISCONNECTED
        markDisconnected()
    }

    /**
     * Al perder la conexion no llegaran mas notificaciones. La placa bloquea
     * y libera las salidas momentaneas, asi que reflejamos ese estado.
     */
    private fun markDisconnected() {
        synchronized(commandLock) {
            commandQueue.clear()
            writeInProgress = false
        }

        _statusAlarm.update {
            it.copy(
                connected = false,
                locked = true,
                starterActive = false,
                button1Active = false,
                button2Active = false
            )
        }
    }

    fun sendCommand(command: String) {
        val encrypted = try {
            aes.aesEncrypt(command.toByteArray(Charsets.UTF_8))
        } catch (e: Exception) {
            // Clave AES con longitud invalida (debe tener 16, 24 o 32 caracteres)
            Log.e(TAG, "No se pudo cifrar el comando", e)
            return
        }

        synchronized(commandLock) {
            commandQueue.addLast(encrypted)
            if (!writeInProgress) {
                writeNextLocked()
            }
        }
    }

    /**
     * Debe llamarse con [commandLock] tomado.
     */
    @SuppressLint("MissingPermission")
    private fun writeNextLocked() {
        val gatt = bluetoothGatt
        if (gatt == null) {
            commandQueue.clear()
            writeInProgress = false
            return
        }

        val service = gatt.getService(BleConstants.SERVICE_UUID)
        val characteristic = service?.getCharacteristic(BleConstants.CHARACTERISTIC_UUID_RX)
        if (characteristic == null) {
            Log.e(TAG, "RX characteristic not found; comando descartado")
            commandQueue.clear()
            writeInProgress = false
            return
        }

        val next = commandQueue.removeFirstOrNull()
        if (next == null) {
            writeInProgress = false
            return
        }

        writeInProgress = true
        characteristic.value = next

        val success = gatt.writeCharacteristic(characteristic)
        if (!success) {
            // Puede haber otra operacion GATT en curso (por ejemplo el
            // descriptor de notificaciones). Dejamos el comando al frente
            // y lo reintentamos cuando llegue el callback correspondiente
            // o en el proximo envio.
            commandQueue.addFirst(next)
            writeInProgress = false
            Log.w(TAG, "writeCharacteristic devolvio false; comando en cola")
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
                    // Descubrir servicios SIEMPRE al conectar. Antes solo se hacia
                    // si el telefono ya estaba emparejado, por lo que la primera
                    // conexion se quedaba en "Conectando..." para siempre.
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(TAG, "Disconnected from GATT server")
                    _connectionState.value = ConnectionState.DISCONNECTED
                    markDisconnected()
                    bluetoothGatt?.close()
                    bluetoothGatt = null
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
                    _statusAlarm.update { it.copy(connected = true) }
                    _connectionState.value = ConnectionState.CONNECTED
                } else {
                    Log.e(TAG, "Service ${BleConstants.SERVICE_UUID} not found")
                    _connectionState.value = ConnectionState.DISCONNECTED
                    markDisconnected()
                }
            } else {
                Log.w(TAG, "onServicesDiscovered received: $status")
                _connectionState.value = ConnectionState.DISCONNECTED
                markDisconnected()
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == BleConstants.CHARACTERISTIC_UUID_STATUS) {
                try {
                    val value = characteristic.value
                    val message = String(aes.aesDecrypt(value), Charsets.UTF_8)
                    val parsed = StatusAlarm.parseStatus(message)
                    if (parsed != null) {
                        _statusAlarm.value = parsed
                        Log.i(TAG, "Status message received: $message")
                    } else {
                        Log.w(TAG, "Estado con formato invalido: $message")
                    }
                } catch (e: Exception) {
                    // Clave distinta o paquete corrupto: no tumbar la app.
                    Log.e(TAG, "No se pudo descifrar el estado recibido", e)
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (characteristic.uuid == BleConstants.CHARACTERISTIC_UUID_RX) {
                synchronized(commandLock) {
                    writeInProgress = false
                    writeNextLocked()
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            // Si un comando quedo encolado mientras se habilitaban las
            // notificaciones, ahora hay via libre para escribirlo.
            synchronized(commandLock) {
                if (!writeInProgress) {
                    writeNextLocked()
                }
            }
        }
    }
}