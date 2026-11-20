package com.example.alarmaapp.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.alarmaapp.ble.BleDevice

@Composable
fun SettingsDialog(
    isScanning: Boolean,
    scannedDevices: List<BleDevice>,
    savedDeviceAddress: String?,
    savedPassword: String,
    onDismiss: () -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onSelectDevice: (BleDevice, String) -> Unit
) {
    var password by remember { mutableStateOf(savedPassword) }
    var selectedDevice by remember { mutableStateOf<BleDevice?>(null) }

    // El ESP32 solo puede descifrar AES-128/192/256, es decir claves de
    // 16, 24 o 32 caracteres. keys.h usa 16 por defecto.
    val normalizedPassword = password.trim()
    val isPasswordValid = normalizedPassword.length == 16 ||
        normalizedPassword.length == 24 ||
        normalizedPassword.length == 32

    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Configuración")
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Password field
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Contraseña BLE (clave AES)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = !isPasswordValid,
                    supportingText = {
                        Text(
                            if (isPasswordValid) {
                                "Debe coincidir con AES_KEY de keys.h"
                            } else {
                                "Usa 16, 24 o 32 caracteres"
                            }
                        )
                    }
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Scan button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Dispositivos BLE",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    if (isScanning) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                            TextButton(onClick = onStopScan) {
                                Text("Detener")
                            }
                        }
                    } else {
                        Button(onClick = onStartScan) {
                            Icon(Icons.Default.Bluetooth, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Escanear")
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Device list
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    items(scannedDevices) { device ->
                        DeviceItem(
                            device = device,
                            isSelected = device.address == savedDeviceAddress,
                            onClick = { selectedDevice = device }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    selectedDevice?.let { device ->
                        onSelectDevice(device, normalizedPassword)
                    }
                    onDismiss()
                },
                enabled = selectedDevice != null && isPasswordValid
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun DeviceItem(
    device: BleDevice,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Seleccionado",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
