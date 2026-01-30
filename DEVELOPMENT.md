# Documentación para Desarrolladores

Esta documentación técnica está diseñada para desarrolladores que quieran entender, modificar o extender la aplicación Alarma BLE.

## Arquitectura de la Aplicación

### Patrón MVVM

La aplicación sigue el patrón Model-View-ViewModel recomendado por Google para aplicaciones Android.

```
┌─────────────┐
│    View     │  Jetpack Compose UI
│  (Compose)  │
└──────┬──────┘
       │ observes
       │ StateFlow
       ▼
┌─────────────┐
│  ViewModel  │  BleViewModel
└──────┬──────┘
       │ uses
       ▼
┌─────────────┐
│ Repository  │  BleRepository
└──────┬──────┘
       │ uses
       ▼
┌─────────────┐
│   Manager   │  BleManager
└──────┬──────┘
       │ communicates
       ▼
┌─────────────┐
│  BLE Device │  ESP32-C3
└─────────────┘
```

### Componentes Principales

#### 1. BleViewModel
**Ubicación:** `viewmodel/BleViewModel.kt`

**Responsabilidades:**
- Mantener el estado de la UI con `StateFlow`
- Coordinar operaciones BLE a través del repositorio
- Manejar eventos de la UI
- Gestionar el ciclo de vida de conexiones BLE

**Estado Principal:**
```kotlin
data class BleUiState(
    val isConnected: Boolean = false,
    val connectionStatus: String = "Desconectado",
    val statusMessage: String = "",
    val isLocked: Boolean = true,
    val isScanning: Boolean = false,
    val scannedDevices: List<BleDevice> = emptyList(),
    val savedDeviceAddress: String? = null,
    val savedDeviceName: String? = null,
    val savedPassword: String = "123456",
    val hasBluetoothPermissions: Boolean = false,
    val isBluetoothEnabled: Boolean = false,
    val errorMessage: String? = null
)
```

**Métodos Clave:**
- `startScan()`: Inicia el escaneo de dispositivos BLE
- `stopScan()`: Detiene el escaneo activo
- `selectDevice()`: Guarda y conecta al dispositivo seleccionado
- `connectToSavedDevice()`: Conecta al dispositivo guardado en preferencias
- `disconnect()`: Desconecta del dispositivo actual
- `toggleLock()`: Envía el comando para cambiar el estado

#### 2. BleRepository
**Ubicación:** `repository/BleRepository.kt`

**Responsabilidades:**
- Capa de abstracción entre ViewModel y BleManager
- Gestión de SharedPreferences para configuración persistente
- Exposición de StateFlows para estados de conexión

**Métodos Clave:**
- `scanDevices()`: Flow de dispositivos escaneados
- `connect(address)`: Inicia conexión a dispositivo
- `sendToggleCommand()`: Envía comando "1"
- `saveDeviceConfig()`: Persiste configuración
- `getSavedDevice*()`: Recupera configuración guardada

#### 3. BleManager
**Ubicación:** `ble/BleManager.kt`

**Responsabilidades:**
- Implementación de bajo nivel de operaciones BLE
- Gestión de BluetoothGatt y callbacks
- Manejo de scan, conexión, descubrimiento de servicios
- Lectura/escritura de características

**Flujo de Conexión:**
```kotlin
connect(address) 
  ↓
onConnectionStateChange(CONNECTED)
  ↓
discoverServices()
  ↓
onServicesDiscovered()
  ↓
enableNotifications(STATUS_CHARACTERISTIC)
  ↓
onCharacteristicChanged() // Recibe actualizaciones
```

**Estados de Conexión:**
```kotlin
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING
}
```

#### 4. Capa de UI (Jetpack Compose)

**MainScreen.kt:**
- Pantalla principal con scaffold
- Manejo de permisos con Accompanist
- Lógica de navegación entre estados (sin permisos, Bluetooth desactivado, contenido principal)

**Componentes:**
- `ConnectionStatus`: Indicador de estado de conexión
- `LockButton`: Botón principal de bloqueo/desbloqueo
- `SettingsDialog`: Diálogo de configuración con escaneo

### Flujo de Datos

#### Escaneo de Dispositivos
```
Usuario presiona "Escanear"
  ↓
ViewModel.startScan()
  ↓
Repository.scanDevices()
  ↓
BleManager.scanDevices() → Flow
  ↓
Callback: onScanResult()
  ↓
Actualiza scannedDevices StateFlow
  ↓
UI recompone con nueva lista
```

#### Cambio de Estado (Bloqueo/Desbloqueo)
```
Usuario presiona botón
  ↓
ViewModel.toggleLock()
  ↓
Repository.sendToggleCommand()
  ↓
BleManager.sendCommand("1")
  ↓
writeCharacteristic(RX_UUID)
  ↓
ESP32 procesa comando
  ↓
ESP32 envía notificación
  ↓
onCharacteristicChanged()
  ↓
Actualiza statusMessage StateFlow
  ↓
UI recompone con nuevo estado
```

## Integración BLE

### UUIDs del Servicio

```kotlin
object BleConstants {
    val SERVICE_UUID = UUID.fromString("e6067851-5971-4b21-a8cc-17738c56ea49")
    val CHARACTERISTIC_UUID_STATUS = UUID.fromString("db9ab4aa-da20-4de8-8a08-e14ab7e5148e")
    val CHARACTERISTIC_UUID_RX = UUID.fromString("f0bf0a71-0dfa-4d1b-90ae-cfda669a37c0")
    val CLIENT_CHARACTERISTIC_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}
```

### Protocolo de Comunicación

#### Escritura (App → ESP32)
```kotlin
// Comando para cambiar estado
val command = "1"
val data = command.toByteArray(Charsets.UTF_8)
characteristic.value = data
gatt.writeCharacteristic(characteristic)
```

#### Lectura (ESP32 → App)
```kotlin
// Recepción de notificación
override fun onCharacteristicChanged(
    gatt: BluetoothGatt,
    characteristic: BluetoothGattCharacteristic
) {
    val value = characteristic.value
    val message = String(value, Charsets.UTF_8)
    // message = "Moto bloqueada." o "Moto desbloqueada."
}
```

### Autenticación/Bonding

El ESP32 requiere PIN estático (123456). El proceso de bonding ocurre automáticamente:

1. App inicia conexión
2. Android muestra diálogo de emparejamiento
3. Usuario ingresa PIN
4. Bonding se completa
5. Futuros intentos de conexión no requieren PIN

**Nota:** La app no muestra el diálogo de PIN directamente; esto lo maneja Android.

## Gestión de Permisos

### Permisos por Versión de Android

```kotlin
fun getRequiredPermissions(): List<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // Android 12+
        listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE
        )
    } else {
        // Android < 12
        listOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }
}
```

### Implementación con Accompanist

```kotlin
val permissionsState = rememberMultiplePermissionsState(
    permissions = PermissionsManager.getRequiredPermissions()
)

LaunchedEffect(permissionsState.allPermissionsGranted) {
    viewModel.updatePermissionsGranted(permissionsState.allPermissionsGranted)
}

if (!permissionsState.allPermissionsGranted) {
    PermissionRequestScreen(
        onRequestPermissions = { 
            permissionsState.launchMultiplePermissionRequest() 
        }
    )
}
```

## Almacenamiento Persistente

### SharedPreferences

La app usa SharedPreferences para guardar:
- Dirección MAC del dispositivo
- Nombre del dispositivo
- Contraseña BLE

```kotlin
private val sharedPreferences: SharedPreferences = 
    context.getSharedPreferences("AlarmaAppPrefs", Context.MODE_PRIVATE)

// Guardar
sharedPreferences.edit().apply {
    putString("device_address", address)
    putString("device_name", name)
    putString("password", password)
    apply()
}

// Recuperar
val address = sharedPreferences.getString("device_address", null)
```

## Temas y Estilos

### Material Design 3

La app usa Material3 con soporte para colores dinámicos en Android 12+:

```kotlin
@Composable
fun AlarmaAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) 
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    // ...
}
```

### Colores Personalizados

```kotlin
val LockedRed = Color(0xFFE53935)      // Rojo para estado bloqueado
val UnlockedGreen = Color(0xFF43A047)  // Verde para desbloqueado
val ConnectedGreen = Color(0xFF4CAF50) // Verde para conectado
val DisconnectedRed = Color(0xFFF44336) // Rojo para desconectado
```

## Modificaciones Comunes

### Agregar Nuevo Comando BLE

1. Agregar constante en `BleConstants.kt`:
```kotlin
const val COMMAND_NEW = "2"
```

2. Agregar método en `BleManager.kt`:
```kotlin
fun sendNewCommand() {
    sendCommand(BleConstants.COMMAND_NEW)
}
```

3. Exponer en `BleRepository.kt`:
```kotlin
fun sendNewCommand() {
    bleManager.sendNewCommand()
}
```

4. Llamar desde `BleViewModel.kt`:
```kotlin
fun executeNewAction() {
    repository.sendNewCommand()
}
```

5. Agregar botón en UI:
```kotlin
Button(onClick = { viewModel.executeNewAction() }) {
    Text("Nueva Acción")
}
```

### Agregar Nueva Característica BLE

1. Agregar UUID en `BleConstants.kt`:
```kotlin
val CHARACTERISTIC_UUID_NEW = UUID.fromString("...")
```

2. Suscribirse en `BleManager.kt`:
```kotlin
override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
    // ... código existente
    val newCharacteristic = service.getCharacteristic(
        BleConstants.CHARACTERISTIC_UUID_NEW
    )
    if (newCharacteristic != null) {
        enableNotifications(newCharacteristic)
    }
}
```

3. Manejar notificaciones:
```kotlin
override fun onCharacteristicChanged(...) {
    when (characteristic.uuid) {
        BleConstants.CHARACTERISTIC_UUID_STATUS -> // existente
        BleConstants.CHARACTERISTIC_UUID_NEW -> {
            val value = String(characteristic.value, Charsets.UTF_8)
            _newValue.value = value
        }
    }
}
```

### Cambiar Timeout de Escaneo

En `BleConstants.kt`:
```kotlin
const val SCAN_TIMEOUT_MS = 20000L  // 20 segundos
```

### Personalizar UI

Los componentes Compose son modulares:

**Cambiar colores del botón:**
```kotlin
// En LockButton.kt
colors = ButtonDefaults.buttonColors(
    containerColor = Color.Blue,  // Tu color
    disabledContainerColor = Color.Gray
)
```

**Cambiar tamaño del botón:**
```kotlin
// En LockButton.kt
modifier = modifier.size(250.dp)  // Más grande
```

## Testing

### Unit Tests

Ejemplo de test para ViewModel:
```kotlin
@Test
fun `test scan updates devices list`() = runTest {
    val viewModel = BleViewModel(application)
    viewModel.startScan()
    
    delay(100)  // Esperar actualización
    
    val state = viewModel.uiState.value
    assertTrue(state.isScanning)
}
```

### Mocks para BLE

Usa Mockito o MockK para mockear BluetoothManager:
```kotlin
val mockBleManager = mockk<BleManager>()
every { mockBleManager.scanDevices() } returns flowOf(devicesList)
```

## Debugging

### Logs BLE

Agregar logs en `BleManager.kt`:
```kotlin
private companion object {
    private const val TAG = "BleManager"
}

Log.d(TAG, "Connecting to device: $deviceAddress")
Log.e(TAG, "Failed to discover services", exception)
```

### Ver Logs en Android Studio

```bash
# Filtrar por tag
adb logcat -s BleManager

# Ver todos los logs de la app
adb logcat | grep com.example.alarmaapp
```

### Debugging con Breakpoints

1. Coloca breakpoints en:
   - `BleManager.onConnectionStateChange()`
   - `BleManager.onCharacteristicChanged()`
   - `BleViewModel` métodos de acción

2. Ejecuta en modo debug desde Android Studio

## Build Variants

### Debug vs Release

**Debug:** Incluye logs, símbolos de debug
```bash
./gradlew assembleDebug
```

**Release:** Optimizado, ofuscado con ProGuard
```bash
./gradlew assembleRelease
```

### ProGuard Rules

Agregar en `app/proguard-rules.pro` si es necesario:
```proguard
# Mantener clases BLE
-keep class com.example.alarmaapp.ble.** { *; }
```

## Recursos Adicionales

### Documentación Oficial
- [Android BLE Overview](https://developer.android.com/guide/topics/connectivity/bluetooth/ble-overview)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [MVVM Architecture](https://developer.android.com/topic/architecture)

### Herramientas
- **nRF Connect**: App para debugging BLE
- **Android Studio Profiler**: Para análisis de rendimiento
- **Layout Inspector**: Para debugging de UI Compose

## Contribuir

### Estándares de Código

1. Seguir [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
2. Usar nombres descriptivos
3. Comentar código complejo
4. Mantener funciones pequeñas y enfocadas

### Pull Requests

1. Fork el repositorio
2. Crea una rama feature
3. Commit con mensajes descriptivos
4. Push y crea PR

### Checklist antes de PR

- [ ] Código compila sin errores
- [ ] Tests pasan
- [ ] Lint sin warnings
- [ ] Documentación actualizada
- [ ] Probado en dispositivo físico

---

**Última Actualización:** Enero 2026  
**Versión de API:** Android 14 (API 34)  
**Lenguaje:** Kotlin 1.9.20
