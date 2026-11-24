# Alarma BLE App

Una aplicación Android completa en Kotlin con Jetpack Compose para controlar un sistema de alarma BLE basado en ESP32-C3.

## Características

- **Interfaz de Usuario Moderna**: Desarrollada con Jetpack Compose y Material Design 3
- **Comunicación BLE**: Conexión y control de dispositivos ESP32-C3 via Bluetooth Low Energy
- **Arquitectura MVVM**: Código bien estructurado siguiendo las mejores prácticas de Android
- **Gestión de Permisos**: Manejo completo de permisos de Bluetooth para Android 12+
- **Escaneo de Dispositivos**: Búsqueda y selección de dispositivos BLE disponibles
- **Control de Alarma**: Bloqueo/desbloqueo del sistema con feedback visual en tiempo real
- **Configuración Persistente**: Almacenamiento de dispositivo y contraseña en SharedPreferences
- **Reconexión Automática**: Intenta conectar automáticamente al dispositivo guardado

## Estructura del Proyecto

```
alarma-app/
├── build.gradle.kts (root)
├── settings.gradle.kts
├── gradle.properties
├── gradlew
├── app/
│   ├── build.gradle.kts
│   ├── src/
│   │   └── main/
│   │       ├── AndroidManifest.xml
│   │       ├── java/com/example/alarmaapp/
│   │       │   ├── MainActivity.kt
│   │       │   ├── AlarmaApplication.kt
│   │       │   ├── ui/
│   │       │   │   ├── screens/
│   │       │   │   │   └── MainScreen.kt
│   │       │   │   ├── components/
│   │       │   │   │   ├── ConnectionStatus.kt
│   │       │   │   │   ├── SettingsDialog.kt
│   │       │   │   │   └── LockButton.kt
│   │       │   │   └── theme/
│   │       │   │       ├── Color.kt
│   │       │   │       ├── Theme.kt
│   │       │   │       └── Type.kt
│   │       │   ├── viewmodel/
│   │       │   │   └── BleViewModel.kt
│   │       │   ├── ble/
│   │       │   │   ├── BleManager.kt
│   │       │   │   ├── BleDevice.kt
│   │       │   │   └── BleConstants.kt
│   │       │   ├── repository/
│   │       │   │   └── BleRepository.kt
│   │       │   └── utils/
│   │       │       └── PermissionsManager.kt
│   │       └── res/
│   │           ├── values/
│   │           │   ├── strings.xml
│   │           │   ├── colors.xml
│   │           │   └── themes.xml
│   │           ├── mipmap-*/
│   │           │   └── ic_launcher.png
│   │           └── drawable/
│   │               └── ic_launcher_foreground.xml
```

## Dispositivo BLE ESP32-C3

### Servicio y Características

**Servicio UUID:** `e6067851-5971-4b21-a8cc-17738c56ea49`

**Características:**
- **STATUS (Notificación):** `db9ab4aa-da20-4de8-8a08-e14ab7e5148e`
  - Envía el estado cifrado con el formato:
    `conectado,bloqueado,encendido,arranque,boton1,boton2`
  - Ejemplo: `1,1,0,0,0,0`
  
- **RX (Escritura):** `f0bf0a71-0dfa-4d1b-90ae-cfda669a37c0`
  - Recibe comandos cifrados:
    - `1` → alterna bloqueo/desbloqueo (relé)
    - `2` → alterna encendido (GPIO 14)
    - `3` / `3u` → arranque presionado / liberado (GPIO 15)
    - `4` / `4u` → botón 1 presionado / liberado (GPIO 15)
    - `5` / `5u` → botón 2 presionado / liberado (GPIO 12)

**Botones momentáneos:** mientras el usuario mantiene presionado un botón,
la app reenvía el comando `down` cada 500 ms. El firmware libera la salida si
pasa 1.5 s sin recibirlo (por ejemplo, si se cierra la app o se pierde el BLE).

**Seguridad:** Autenticación con PIN estático: `123456`

## Requisitos del Sistema

- **minSdk**: 26 (Android 8.0 Oreo)
- **targetSdk**: 34 (Android 14)
- **compileSdk**: 34
- **Kotlin**: 1.9.20
- **Gradle**: 8.2
- **Android Gradle Plugin**: 8.1.4

## Dependencias Principales

- **Jetpack Compose BOM**: 2023.10.01
- **Material3**: Última versión estable
- **Activity Compose**: 1.8.1
- **Lifecycle & ViewModel**: 2.6.2
- **Kotlinx Coroutines**: 1.7.3
- **Accompanist Permissions**: 0.32.0

## Permisos

La aplicación solicita los siguientes permisos:

### Android 12+ (API 31+)
- `BLUETOOTH_SCAN` (sin ubicación)
- `BLUETOOTH_CONNECT`
- `BLUETOOTH_ADVERTISE`

### Android < 12 (API < 31)
- `BLUETOOTH`
- `BLUETOOTH_ADMIN`
- `ACCESS_FINE_LOCATION`

## Uso de la Aplicación

### Primera Vez

1. **Otorgar Permisos**: La app solicitará permisos de Bluetooth necesarios
2. **Activar Bluetooth**: Si no está activo, se solicitará activarlo
3. **Configurar Dispositivo**:
   - Presionar el ícono de configuración (⚙️) en la esquina superior izquierda
   - Presionar "Escanear" para buscar dispositivos BLE
   - Seleccionar el dispositivo ESP32-C3 de la lista
   - Configurar la contraseña (por defecto: 123456)
   - Presionar "Guardar"

### Operación Normal

1. **Conexión Automática**: Al abrir la app, se conecta automáticamente al dispositivo guardado
2. **Ver Estado**: El estado actual se muestra en la esquina superior derecha
   - Verde ✓: Conectado con el estado actual
   - Rojo ✗: Desconectado
3. **Controlar Alarma**:
   - Presionar el botón central grande para cambiar el estado
   - Rojo con candado cerrado: Bloqueado
   - Verde con candado abierto: Desbloqueado
4. **Acciones adicionales**:
   - **Encender**: toggle de GPIO 14 (se mantiene al soltar)
   - **Arrancar**: mantener presionado para activar GPIO 15
   - **Botón 1**: mantener presionado para activar GPIO 15
   - **Botón 2**: mantener presionado para activar GPIO 12

## Arquitectura

### MVVM (Model-View-ViewModel)

- **Model**: `BleRepository`, `BleManager`
- **View**: Composables en `ui/screens` y `ui/components`
- **ViewModel**: `BleViewModel` con StateFlow para estados reactivos

### Flujo de Datos

```
UI (Compose) <-> ViewModel <-> Repository <-> BleManager <-> ESP32 Device
```

### Gestión de Estados

```kotlin
data class BleUiState(
    val isConnected: Boolean,
    val connectionStatus: String,
    val statusMessage: String,
    val isLocked: Boolean,
    val isScanning: Boolean,
    val scannedDevices: List<BleDevice>,
    val savedDeviceAddress: String?,
    val savedDeviceName: String?,
    val savedPassword: String,
    val hasBluetoothPermissions: Boolean,
    val isBluetoothEnabled: Boolean,
    val errorMessage: String?
)
```

## Compilación

```bash
# Compilar APK de debug
./gradlew assembleDebug

# Compilar APK de release
./gradlew assembleRelease

# Instalar en dispositivo conectado
./gradlew installDebug

# Ejecutar tests
./gradlew test
```

## Funcionalidades Implementadas

✅ Escaneo de dispositivos BLE con filtro por UUID de servicio  
✅ Conexión y desconexión de dispositivos  
✅ Autenticación con PIN (bonding/pairing)  
✅ Suscripción a notificaciones de característica STATUS  
✅ Escritura de comandos a característica RX  
✅ Decodificación de mensajes UTF-8 desde el dispositivo  
✅ UI reactiva con StateFlow  
✅ Gestión completa de permisos  
✅ Almacenamiento persistente de configuración  
✅ Reconexión automática  
✅ Feedback visual del estado de conexión y bloqueo  
✅ Diseño Material Design 3  

## Notas Técnicas

### Decodificación de Mensajes

Los mensajes del dispositivo ESP32 llegan como arrays de bytes UTF-8:
```kotlin
val message = String(byteArray, Charsets.UTF_8)
```

### Envío de Comandos

Los comandos se cifran con AES-CBC antes de escribirlos en la característica RX.
Por ejemplo, para alternar el bloqueo se envía el string `"1"`:
```kotlin
val command = "1".toByteArray(Charsets.UTF_8)
characteristic.value = aes.aesEncrypt(command)
gatt.writeCharacteristic(characteristic)
```

Tabla completa de comandos:

| Comando | Acción | GPIO |
|---------|--------|------|
| `1` | Alterna bloqueo/desbloqueo | Relé |
| `2` | Alterna encendido/apagado | 14 |
| `3` / `3u` | Arranque presionado / liberado | 15 |
| `4` / `4u` | Botón 1 presionado / liberado | 15 |
| `5` / `5u` | Botón 2 presionado / liberado | 12 |

### Habilitación de Notificaciones

```kotlin
gatt.setCharacteristicNotification(characteristic, true)
val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
gatt.writeDescriptor(descriptor)
```

## Mejoras Futuras

- [ ] Agregar logs de eventos
- [ ] Implementar historial de conexiones
- [ ] Soporte para múltiples dispositivos
- [ ] Notificaciones push cuando cambia el estado
- [ ] Widget de pantalla de inicio
- [ ] Modo oscuro/claro manual
- [ ] Tests unitarios e instrumentados
- [ ] Métricas de uso con Analytics

## Licencia

Este proyecto está bajo la licencia MIT.

## Autor

Desarrollado para el control de sistemas de alarma BLE basados en ESP32-C3.
