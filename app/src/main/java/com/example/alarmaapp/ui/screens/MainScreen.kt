package com.example.alarmaapp.ui.screens

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.alarmaapp.ui.components.ConnectionStatus
import com.example.alarmaapp.ui.components.LockButton
import com.example.alarmaapp.ui.components.ConnectButton
import com.example.alarmaapp.ui.components.SettingsDialog
import com.example.alarmaapp.utils.PermissionsManager
import com.example.alarmaapp.viewmodel.BleViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(
    viewModel: BleViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showSettingsDialog by remember { mutableStateOf(false) }
    
    // Permissions handling
    val permissionsState = rememberMultiplePermissionsState(
        permissions = PermissionsManager.getRequiredPermissions()
    )
    
    // Bluetooth enable launcher
    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.updateBluetoothEnabled()
        }
    }
    
    // Check permissions on launch
    LaunchedEffect(permissionsState.allPermissionsGranted) {
        viewModel.updatePermissionsGranted(permissionsState.allPermissionsGranted)
        
        if (permissionsState.allPermissionsGranted && uiState.isBluetoothEnabled) {
            // Auto-connect if device is saved
            if (uiState.savedDeviceAddress != null && !uiState.isConnected) {
                viewModel.connectToSavedDevice()
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Alarma BLE") },
                navigationIcon = {
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Configuración"
                        )
                    }
                },
                actions = {
                    ConnectionStatus(
                        isConnected = uiState.isConnected,
                        statusText = if (uiState.isConnected && uiState.statusMessage.isNotEmpty()) {
                            uiState.statusMessage
                        } else {
                            uiState.connectionStatus
                        }
                    )
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            when {
                !permissionsState.allPermissionsGranted -> {
                    PermissionRequestScreen(
                        onRequestPermissions = { permissionsState.launchMultiplePermissionRequest() }
                    )
                }
                !uiState.isBluetoothEnabled -> {
                    BluetoothEnableScreen(
                        onEnableBluetooth = {
                            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                            enableBluetoothLauncher.launch(enableBtIntent)
                        }
                    )
                }
                else -> {
                    MainContent(
                        isLocked = uiState.isLocked,
                        isConnected = uiState.isConnected,
                        onToggleLock = { viewModel.toggleLock() },
                        onConnect = {
                            if (uiState.isConnected) viewModel.disconnect() else {
                                if (!permissionsState.allPermissionsGranted) permissionsState.launchMultiplePermissionRequest()
                                viewModel.connectToSavedDevice()
                            }
                        }
                    )
                }
            }
        }
    }
    
    if (showSettingsDialog) {
        SettingsDialog(
            isScanning = uiState.isScanning,
            scannedDevices = uiState.scannedDevices,
            savedDeviceAddress = uiState.savedDeviceAddress,
            savedPassword = uiState.savedAESKey,
            onDismiss = { 
                viewModel.stopScan()
                showSettingsDialog = false 
            },
            onStartScan = { viewModel.startScan() },
            onStopScan = { viewModel.stopScan() },
            onSelectDevice = { device, password ->
                viewModel.selectDevice(device, password)
                // Disconnect from current device if connected
                if (uiState.isConnected) {
                    viewModel.disconnect()
                }
                // Connect to new device
                viewModel.connectToSavedDevice()
            }
        )
    }
}

@Composable
fun MainContent(
    isLocked: Boolean,
    isConnected: Boolean,
    onToggleLock: () -> Unit,
    onConnect: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
//        TODO: colocar los elementos en cuadricula
        ConnectButton(
            isConnected = isConnected,
            onToggle = onConnect
        )
        Spacer(modifier = Modifier.height(16.dp))
        LockButton(
            isLocked = isLocked,
            isConnected = isConnected,
            onToggle = onToggleLock
        )
    }
}

@Composable
fun PermissionRequestScreen(
    onRequestPermissions: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Esta aplicación requiere permisos de Bluetooth para funcionar.")
        Button(
            onClick = onRequestPermissions,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text("Solicitar Permisos")
        }
    }
}

@Composable
fun BluetoothEnableScreen(
    onEnableBluetooth: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Bluetooth está desactivado. Por favor, actívalo para continuar.")
        Button(
            onClick = onEnableBluetooth,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text("Activar Bluetooth")
        }
    }
}
