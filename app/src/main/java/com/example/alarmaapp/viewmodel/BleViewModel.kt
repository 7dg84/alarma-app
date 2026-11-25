package com.example.alarmaapp.viewmodel

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import com.example.alarmaapp.AlarmaApplication
import androidx.lifecycle.viewModelScope
import com.example.alarmaapp.ble.BleConstants
import com.example.alarmaapp.ble.BleDevice
import com.example.alarmaapp.ble.BleManager
import com.example.alarmaapp.service.BleServiceController
import com.example.alarmaapp.utils.PermissionsManager
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
    val backgroundServiceEnabled: Boolean = true,
    val reconnectEnabled: Boolean = true,
    val autoConnectEnabled: Boolean = true,
    val autoStartEnabled: Boolean = false,
    val errorMessage: String? = null
)

class BleViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(BleUiState())

    // Misma instancia que usan el servicio y el widget: la conexion BLE se comparte.
    private val app = application as AlarmaApplication
    private val repository = app.bleRepository
    private val appSettings = app.appSettings
    val uiState: StateFlow<BleUiState> = _uiState.asStateFlow()
    
    private var scanJob: Job? = null
    private var starterHoldJob: Job? = null
    private var button1HoldJob: Job? = null
    private var button2HoldJob: Job? = null
    
    init {
        loadSavedConfig()
        observeSettings()
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

    private fun observeSettings() {
        viewModelScope.launch {
            appSettings.values.collect { values ->
                _uiState.update { state ->
                    state.copy(
                        backgroundServiceEnabled = values.backgroundServiceEnabled,
                        reconnectEnabled = values.reconnectEnabled,
                        autoConnectEnabled = values.autoConnectEnabled,
                        autoStartEnabled = values.autoStartEnabled
                    )
                }
            }
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
        // Reconecta para aplicar el dispositivo/clave nuevos. El controlador
        // decide si lo hace a traves del servicio o en modo directo.
        BleServiceController.reconnect(app)
    }
    
    fun connectToSavedDevice() {
        // Evita reconectar si el servicio o el widget ya iniciaron la conexion.
        val state = repository.connectionState.value
        if (state == BleManager.ConnectionState.CONNECTED ||
            state == BleManager.ConnectionState.CONNECTING ||
            state == BleManager.ConnectionState.DISCONNECTING
        ) {
            return
        }

        if (_uiState.value.savedDeviceAddress == null) return
        BleServiceController.connect(app)
    }
    
    fun disconnect() {
        BleServiceController.disconnect(app)
    }

    fun toggleConnect() {
        if (_uiState.value.isConnected) disconnect() else connectToSavedDevice()
    }

    // ---------------------------------------------------------------------
    // Ajustes de segundo plano
    // ---------------------------------------------------------------------

    fun setBackgroundServiceEnabled(enabled: Boolean) {
        appSettings.setBackgroundServiceEnabled(enabled)
        if (enabled) {
            val connected = repository.connectionState.value ==
                BleManager.ConnectionState.CONNECTED
            if (connected) {
                // Si ya habia una conexion directa, el servicio la adopta.
                BleServiceController.ensureRunning(app)
            } else if (_uiState.value.autoConnectEnabled &&
                _uiState.value.savedDeviceAddress != null
            ) {
                BleServiceController.connect(app)
            }
        } else {
            BleServiceController.disconnect(app)
        }
    }

    fun setReconnectEnabled(enabled: Boolean) {
        appSettings.setReconnectEnabled(enabled)
        BleServiceController.notifySettingsChanged(app)
    }

    fun setAutoConnectEnabled(enabled: Boolean) {
        appSettings.setAutoConnectEnabled(enabled)
    }

    fun setAutoStartEnabled(enabled: Boolean) {
        appSettings.setAutoStartEnabled(enabled)
    }

    fun shouldAskNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        return appSettings.values.value.backgroundServiceEnabled &&
            !PermissionsManager.hasNotificationPermission(app) &&
            !appSettings.wasNotificationPermissionAsked()
    }

    fun markNotificationPermissionAsked() {
        appSettings.markNotificationPermissionAsked()
    }

    fun refreshNotificationIfGranted() {
        if (PermissionsManager.hasNotificationPermission(app)) {
            BleServiceController.refreshNotification(app)
        }
    }

    fun refreshNotification() {
        BleServiceController.refreshNotification(app)
    }
    
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
