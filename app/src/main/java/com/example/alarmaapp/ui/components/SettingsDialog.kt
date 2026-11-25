package com.example.alarmaapp.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Switch
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
    backgroundServiceEnabled: Boolean,
    reconnectEnabled: Boolean,
    autoConnectEnabled: Boolean,
    autoStartEnabled: Boolean,
    onDismiss: () -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onSelectDevice: (BleDevice, String) -> Unit,
    onBackgroundServiceChange: (Boolean) -> Unit,
    onReconnectChange: (Boolean) -> Unit,
    onAutoConnectChange: (Boolean) -> Unit,
    onAutoStartChange: (Boolean) -> Unit
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
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
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

                // Ajustes de segundo plano. Se aplican al instante: no hace
                // falta seleccionar un dispositivo para que tengan efecto.
                Text(
                    text = "Segundo plano",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Estos cambios se aplican al instante.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                SettingSwitchRow(
                    title = "Servicio en segundo plano",
                    subtitle = "Mantiene la conexión BLE aunque cierres la app.",
                    checked = backgroundServiceEnabled,
                    onCheckedChange = onBackgroundServiceChange
                )

                SettingSwitchRow(
                    title = "Reconexión automática",
                    subtitle = "Vuelve a conectar si se pierde la señal o se apaga el Bluetooth.",
                    checked = reconnectEnabled,
                    onCheckedChange = onReconnectChange
                )

                SettingSwitchRow(
                    title = "Conexión automática",
                    subtitle = "Conecta al abrir la app si hay un dispositivo guardado.",
                    checked = autoConnectEnabled,
                    onCheckedChange = onAutoConnectChange
                )

                SettingSwitchRow(
                    title = "Inicio automático",
                    subtitle = "Inicia el servicio al encender el teléfono.",
                    checked = autoStartEnabled,
                    onCheckedChange = onAutoStartChange
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
                scannedDevices.forEach { device ->
                    DeviceItem(
                        device = device,
                        isSelected = device.address == savedDeviceAddress,
                        onClick = { selectedDevice = device }
                    )
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
                Text("Cerrar")
            }
        }
    )
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = null
        )
    }
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
