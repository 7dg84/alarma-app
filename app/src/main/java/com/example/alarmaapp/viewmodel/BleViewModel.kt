package com.example.alarmaapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.alarmaapp.AlarmaApplication
import androidx.lifecycle.viewModelScope
import com.example.alarmaapp.ble.BleConstants
import com.example.alarmaapp.ble.BleDevice
import com.example.alarmaapp.ble.BleManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class BleUiState(
    val isConnected: Boolean = false,
    val connectionStatus: String = "Desconectado",
    val statusMessage: String = "",
    val isLocked: Boolean = true,
    val isPowerOn: Boolean = false,
    val isStarterActive: Boolean = false,
    val isButton1Active: Boolean = false,
    val isButton2Active: Boolean = false,
    val isScanning: Boolean = false,
    val scannedDevices: List<BleDevice> = emptyList(),
    val savedDeviceAddress: String? = null,
    val savedDeviceName: String? = null,
    val savedAESKey: String = BleConstants.DEFAULT_KEY,
    val hasBluetoothPermissions: Boolean = false,
    val isBluetoothEnabled: Boolean = false,
    val errorMessage: String? = null
)

class BleViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(BleUiState())

    // Misma instancia que usa el widget: la conexion BLE se comparte.
    private val repository = (application as AlarmaApplication).bleRepository
    val uiState: StateFlow<BleUiState> = _uiState.asStateFlow()
    
    private var scanJob: Job? = null
    private var starterHoldJob: Job? = null
    private var button1HoldJob: Job? = null
    private var button2HoldJob: Job? = null
    
    init {
        loadSavedConfig()
        observeConnectionState()
        observeStatus()
        updateBluetoothEnabled()
    }
    
    private fun loadSavedConfig() {
        _uiState.update { state ->
            state.copy(
                savedDeviceAddress = repository.getSavedDeviceAddress(),
                savedDeviceName = repository.getSavedDeviceName(),
                savedAESKey = repository.getSavedPassword()
            )
        }
    }
    
    private fun observeConnectionState() {
        viewModelScope.launch {
            repository.connectionState.collect { connectionState ->
                val isConnected = connectionState == BleManager.ConnectionState.CONNECTED
                val status = when (connectionState) {
                    BleManager.ConnectionState.DISCONNECTED -> "Desconectado"
                    BleManager.ConnectionState.CONNECTING -> "Conectando..."
                    BleManager.ConnectionState.CONNECTED -> "Conectado"
                    BleManager.ConnectionState.DISCONNECTING -> "Desconectando..."
                }
                
                if (!isConnected) {
                    // Sin conexion no tiene sentido seguir reenviando keep-alives.
                    cancelHoldJobs()
                }

                _uiState.update { state ->
                    state.copy(
                        isConnected = isConnected,
                        connectionStatus = status,
                        isStarterActive = if (isConnected) state.isStarterActive else false,
                        isButton1Active = if (isConnected) state.isButton1Active else false,
                        isButton2Active = if (isConnected) state.isButton2Active else false
                    )
                }
            }
        }
    }
    
    private fun observeStatus() {
        viewModelScope.launch {
            repository.statusAlarm.collect { status ->
                _uiState.update { state ->
                    state.copy(
                        isLocked = status.locked,
                        isPowerOn = status.powerOn,
                        isStarterActive = status.starterActive,
                        isButton1Active = status.button1Active,
                        isButton2Active = status.button2Active,
                        statusMessage = if (status.locked) "Bloqueada." else "Desbloqueada"
                    )
                }
            }
        }
    }
    
    fun updateBluetoothEnabled() {
        val enabled = repository.isBluetoothEnabled()
        _uiState.update { it.copy(isBluetoothEnabled = enabled) }
    }
    
    fun updatePermissionsGranted(granted: Boolean) {
        _uiState.update { it.copy(hasBluetoothPermissions = granted) }
    }
    
    fun startScan() {
        if (_uiState.value.isScanning) return
        
        _uiState.update { it.copy(isScanning = true, scannedDevices = emptyList()) }
        
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            repository.scanDevices().collect { devices ->
                _uiState.update { it.copy(scannedDevices = devices) }
            }
        }
        
        // Stop scanning after timeout
        viewModelScope.launch {
            delay(BleConstants.SCAN_TIMEOUT_MS)
            stopScan()
        }
    }
    
    fun stopScan() {
        scanJob?.cancel()
        _uiState.update { it.copy(isScanning = false) }
    }
    
    fun selectDevice(device: BleDevice, password: String) {
        repository.saveDeviceConfig(device.address, device.name, password)
        _uiState.update { state ->
            state.copy(
                savedDeviceAddress = device.address,
                savedDeviceName = device.name,
                savedAESKey = password
            )
        }
    }
    
    fun connectToSavedDevice() {
        // Evita reconectar si el widget (o la app) ya inicio la conexion.
        val state = repository.connectionState.value
        if (state == BleManager.ConnectionState.CONNECTED ||
            state == BleManager.ConnectionState.CONNECTING
        ) {
            return
        }

        val address = _uiState.value.savedDeviceAddress
        if (address != null) {
            repository.connect(address)
        }
    }
    
    fun disconnect() {
        repository.disconnect()
    }

    fun toggleConnect() {}
    
    fun toggleLock() {
        repository.sendToggleCommand()
    }

    /** Enciende / apaga la salida GPIO 14. */
    fun togglePower() {
        if (!_uiState.value.isConnected) return
        repository.sendCommand(BleConstants.COMMAND_POWER_TOGGLE)
    }

    /** Mantener presionado "Arrancar": GPIO 15 activo solo mientras dura. */
    fun pressStarter() {
        starterHoldJob?.cancel()
        starterHoldJob = startHold(BleConstants.COMMAND_STARTER_DOWN)
    }

    fun releaseStarter() {
        starterHoldJob?.cancel()
        starterHoldJob = null
        sendRelease(BleConstants.COMMAND_STARTER_UP)
    }

    /** Mantener presionado "Boton 1": GPIO 15 activo solo mientras dura. */
    fun pressButton1() {
        button1HoldJob?.cancel()
        button1HoldJob = startHold(BleConstants.COMMAND_BUTTON1_DOWN)
    }

    fun releaseButton1() {
        button1HoldJob?.cancel()
        button1HoldJob = null
        sendRelease(BleConstants.COMMAND_BUTTON1_UP)
    }

    /** Mantener presionado "Boton 2": GPIO 12 activo solo mientras dura. */
    fun pressButton2() {
        button2HoldJob?.cancel()
        button2HoldJob = startHold(BleConstants.COMMAND_BUTTON2_DOWN)
    }

    fun releaseButton2() {
        button2HoldJob?.cancel()
        button2HoldJob = null
        sendRelease(BleConstants.COMMAND_BUTTON2_UP)
    }

    /**
     * Envia el comando "down" de inmediato y lo repite cada
     * [BleConstants.HOLD_REFRESH_MS] mientras el boton siga presionado.
     * El firmware apaga la salida si deja de recibirlo (watchdog).
     */
    private fun startHold(downCommand: String): Job? {
        if (!_uiState.value.isConnected) return null

        return viewModelScope.launch {
            repository.sendCommand(downCommand)
            while (isActive) {
                delay(BleConstants.HOLD_REFRESH_MS)
                repository.sendCommand(downCommand)
            }
        }
    }

    private fun sendRelease(upCommand: String) {
        if (_uiState.value.isConnected) {
            repository.sendCommand(upCommand)
        }
    }

    private fun cancelHoldJobs() {
        starterHoldJob?.cancel()
        starterHoldJob = null
        button1HoldJob?.cancel()
        button1HoldJob = null
        button2HoldJob?.cancel()
        button2HoldJob = null
    }

    /**
     * Libera cualquier boton momentaneo que siga activo. Se llama al cerrar
     * la app o cuando pasa a segundo plano (ON_STOP).
     */
    fun releaseAllHolds() {
        if (starterHoldJob != null) sendRelease(BleConstants.COMMAND_STARTER_UP)
        if (button1HoldJob != null) sendRelease(BleConstants.COMMAND_BUTTON1_UP)
        if (button2HoldJob != null) sendRelease(BleConstants.COMMAND_BUTTON2_UP)
        cancelHoldJobs()
    }

    override fun onCleared() {
        // No desconectar: el widget debe seguir usando la conexion. Pero si
        // el usuario cierra la app con un boton presionado, liberamos las
        // salidas momentaneas.
        releaseAllHolds()
        super.onCleared()
    }
}
