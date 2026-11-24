package com.example.alarmaapp.ui.screens

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LooksOne
import androidx.compose.material.icons.filled.LooksTwo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.alarmaapp.ui.components.BoardStatus
import com.example.alarmaapp.ui.components.ConnectionStatus
import com.example.alarmaapp.ui.components.ConnectButton
import com.example.alarmaapp.ui.components.HoldActionButton
import com.example.alarmaapp.ui.components.LockButton
import com.example.alarmaapp.ui.components.SettingsDialog
import com.example.alarmaapp.ui.components.ToggleActionButton
import com.example.alarmaapp.ui.theme.UnlockedGreen
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
    val lifecycleOwner = LocalLifecycleOwner.current
    var showSettingsDialog by remember { mutableStateOf(false) }

    // Si la app pasa a segundo plano con un boton momentaneo presionado,
    // liberamos las salidas para no dejar el arranque activo.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.releaseAllHolds()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    
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
                        isPowerOn = uiState.isPowerOn,
                        isStarterActive = uiState.isStarterActive,
                        isButton1Active = uiState.isButton1Active,
                        isButton2Active = uiState.isButton2Active,
                        onToggleLock = { viewModel.toggleLock() },
                        onTogglePower = { viewModel.togglePower() },
                        onStarterPress = { viewModel.pressStarter() },
                        onStarterRelease = { viewModel.releaseStarter() },
                        onButton1Press = { viewModel.pressButton1() },
                        onButton1Release = { viewModel.releaseButton1() },
                        onButton2Press = { viewModel.pressButton2() },
                        onButton2Release = { viewModel.releaseButton2() },
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
    isPowerOn: Boolean,
    isStarterActive: Boolean,
    isButton1Active: Boolean,
    isButton2Active: Boolean,
    onToggleLock: () -> Unit,
    onTogglePower: () -> Unit,
    onStarterPress: () -> Unit,
    onStarterRelease: () -> Unit,
    onButton1Press: () -> Unit,
    onButton1Release: () -> Unit,
    onButton2Press: () -> Unit,
    onButton2Release: () -> Unit,
    onConnect: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
//        ConnectButton(
//            isConnected = isConnected,
//            onToggle = onConnect
//        )
//        Spacer(modifier = Modifier.height(16.dp))
//        LockButton(
//            isLocked = isLocked,
//            isConnected = isConnected,
//            onToggle = onToggleLock
//        )
//
//        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Acciones",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ConnectButton(
                isConnected = isConnected,
                onToggle = onConnect,
                modifier = Modifier.weight(1f)
            )
            LockButton(
                isLocked = isLocked,
                isConnected = isConnected,
                onToggle = onToggleLock,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ToggleActionButton(
                label = "Encender",
                activeLabel = "Apagar",
                isActive = isPowerOn,
                isConnected = isConnected,
                icon = Icons.Default.PowerSettingsNew,
                onToggle = onTogglePower,
                modifier = Modifier.weight(1f)
            )
            HoldActionButton(
                label = "Arrancar",
                activeLabel = "Arrancando",
                isActive = isStarterActive,
                isConnected = isConnected && isPowerOn,
                icon = Icons.Default.PlayArrow,
                onPressStart = onStarterPress,
                onPressEnd = onStarterRelease,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HoldActionButton(
                label = "Botón 1",
                activeLabel = "Botón 1",
                isActive = isButton1Active,
                isConnected = isConnected,
                icon = Icons.Default.LooksOne,
                onPressStart = onButton1Press,
                onPressEnd = onButton1Release,
                modifier = Modifier.weight(1f),
                activeColor = UnlockedGreen
            )
            HoldActionButton(
                label = "Botón 2",
                activeLabel = "Botón 2",
                isActive = isButton2Active,
                isConnected = isConnected,
                icon = Icons.Default.LooksTwo,
                onPressStart = onButton2Press,
                onPressEnd = onButton2Release,
                modifier = Modifier.weight(1f),
                activeColor = UnlockedGreen
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        BoardStatus(
            isConnected = isConnected,
            isLocked = isLocked,
            isPowerOn = isPowerOn,
            isStarterActive = isStarterActive,
            isButton1Active = isButton1Active,
            isButton2Active = isButton2Active
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
