package com.example.alarmaapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.alarmaapp.ble.BleConstants
import com.example.alarmaapp.ble.BleDevice
import com.example.alarmaapp.ble.BleManager
import com.example.alarmaapp.repository.BleRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BleUiState(
    val isConnected: Boolean = false,
    val connectionStatus: String = "Desconectado",
    val statusMessage: String = "",
    val isLocked: Boolean = true,
    val isScanning: Boolean = false,
    val scannedDevices: List<BleDevice> = emptyList(),
    val savedDeviceAddress: String? = null,
    val savedDeviceName: String? = null,
    val savedPassword: String = BleConstants.DEFAULT_PIN,
    val hasBluetoothPermissions: Boolean = false,
    val isBluetoothEnabled: Boolean = false,
    val errorMessage: String? = null
)

class BleViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository = BleRepository(application)
    
    private val _uiState = MutableStateFlow(BleUiState())
    val uiState: StateFlow<BleUiState> = _uiState.asStateFlow()
    
    private var scanJob: Job? = null
    
    init {
        loadSavedConfig()
        observeConnectionState()
        observeStatusMessage()
        updateBluetoothEnabled()
    }
    
    private fun loadSavedConfig() {
        _uiState.update { state ->
            state.copy(
                savedDeviceAddress = repository.getSavedDeviceAddress(),
                savedDeviceName = repository.getSavedDeviceName(),
                savedPassword = repository.getSavedPassword()
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
                
                _uiState.update { state ->
                    state.copy(
                        isConnected = isConnected,
                        connectionStatus = status
                    )
                }
            }
        }
    }
    
    private fun observeStatusMessage() {
        viewModelScope.launch {
            repository.statusMessage.collect { message ->
                if (message.isNotEmpty()) {
                    val isLocked = message.contains("bloqueada", ignoreCase = true)
                    _uiState.update { state ->
                        state.copy(
                            statusMessage = message,
                            isLocked = isLocked
                        )
                    }
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
                savedPassword = password
            )
        }
    }
    
    fun connectToSavedDevice() {
        val address = _uiState.value.savedDeviceAddress
        if (address != null) {
            repository.connect(address)
        }
    }
    
    fun disconnect() {
        repository.disconnect()
    }
    
    fun toggleLock() {
        repository.sendToggleCommand()
    }
    
    override fun onCleared() {
        super.onCleared()
        repository.disconnect()
    }
}
