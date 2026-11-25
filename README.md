# Alarma BLE App

Aplicación Android en Kotlin con Jetpack Compose para controlar un sistema de alarma para motocicleta basado en **ESP32 / ESP32-C3** mediante Bluetooth Low Energy (BLE) y comunicación cifrada con **AES-CBC**.

## Características

- **Interfaz de Usuario Moderna**: Desarrollada con Jetpack Compose y Material Design 3.
- **Comunicación BLE Cifrada**: Conexión GATT con cifrado AES-CBC (PKCS5/PKCS7) para comandos y notificaciones de estado.
- **Servicio en Primer Plano (`BleForegroundService`)**: Mantiene viva la conexión BLE en segundo plano con notificación persistente del estado de conexión y bloqueo.
- **Reconexión Automática y Watchdog**: Reintenta la conexión con backoff exponencial (`3s, 5s, 10s, 20s, 30s, 60s`) y reinicia intentos colgados tras 20 s.
- **Widget de Pantalla de Inicio**: Widget compacto (1×2) para **Conectar/Desconectar** y **Bloquear/Desbloquear** sin abrir la app.
- **Inicio Automático**: Opcionalmente inicia el servicio BLE al encender el teléfono (`BootReceiver`).
- **Controles de Moto y Estado en Tiempo Real**: Bloqueo/desbloqueo, encendido/apagado, arranque y 2 botones auxiliares momentáneos con panel visual de estado de la placa.
- **Arquitectura MVVM**: Estado reactivo centralizado con `StateFlow` y repositorio compartido entre `MainActivity`, servicio y widget.
- **Gestión de Permisos**: Manejo completo de permisos Bluetooth (Android 12+ y versiones anteriores) y notificaciones (Android 13+).

## Estructura del Proyecto

```
alarma-app/
├── build.gradle.kts (root)
├── settings.gradle.kts
├── gradle.properties
├── gradlew
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/example/alarmaapp/
        │   ├── AlarmaApplication.kt
        │   ├── MainActivity.kt
        │   ├── ble/
        │   │   ├── AES.kt
        │   │   ├── BleConstants.kt
        │   │   ├── BleDevice.kt
        │   │   └── BleManager.kt
        │   ├── repository/
        │   │   └── BleRepository.kt
        │   ├── service/
        │   │   ├── BleForegroundService.kt
        │   │   ├── BleServiceController.kt
        │   │   └── BootReceiver.kt
        │   ├── settings/
        │   │   └── AppSettings.kt
        │   ├── ui/
        │   │   ├── components/
        │   │   │   ├── ActionButtons.kt
        │   │   │   ├── BoardStatus.kt
        │   │   │   ├── ConnectButton.kt
        │   │   │   ├── ConnectionStatus.kt
        │   │   │   ├── LockButton.kt
        │   │   │   └── SettingsDialog.kt
        │   │   ├── screens/
        │   │   │   └── MainScreen.kt
        │   │   └── theme/
        │   │       ├── Color.kt
        │   │       ├── Theme.kt
        │   │       └── Type.kt
        │   ├── utils/
        │   │   └── PermissionsManager.kt
        │   ├── viewmodel/
        │   │   └── BleViewModel.kt
        │   └── widget/
        │       ├── AlarmaWidget.kt
        │       └── WidgetActionReceiver.kt
        └── res/
            ├── drawable/
            │   ├── ic_launcher_foreground.xml
            │   ├── ic_stat_alarma.xml
            │   └── widget_*.xml
            ├── layout/
            │   └── widget_alarma.xml
            ├── mipmap-*/
            │   └── ic_launcher*.png
            ├── values/
            │   ├── colors.xml
            │   ├── strings.xml
            │   └── themes.xml
            └── xml/
                └── alarma_widget_info.xml
```

## Dispositivo BLE (ESP32 / ESP32-C3)

### Servicio y Características

- **Service UUID:** `e6067851-5971-4b21-a8cc-17738c56ea49`
- **STATUS (Notificación):** `db9ab4aa-da20-4de8-8a08-e14ab7e5148e`
  - Envía el estado cifrado con AES-CBC en formato CSV:
    `conectado,bloqueado,encendido,arranque,boton1,boton2`
  - Ejemplo: `1,1,0,0,0,0`
- **RX (Escritura):** `f0bf0a71-0dfa-4d1b-90ae-cfda669a37c0`
  - Recibe comandos cifrados con AES-CBC:
    - `1` → alterna bloqueo/desbloqueo (Relé en GPIO `2`)
    - `2` → alterna encendido/apagado (`POWER_PIN`, GPIO `14`)
    - `3` / `3u` → arranque presionado / liberado (`STARTER_PIN`, GPIO `12`, activo en `LOW` cuando está encendido y desbloqueado)
    - `4` / `4u` → botón 1 presionado / liberado (`BUTTON1_PIN`, GPIO `4`)
    - `5` / `5u` → botón 2 presionado / liberado (`BUTTON2_PIN`, GPIO `4`)

**Botones momentáneos:** Mientras el usuario mantiene presionado `Arrancar`, `Botón 1` o `Botón 2`, la app reenvía el comando `down` cada **500 ms** (`HOLD_REFRESH_MS`). Si la app pasa a segundo plano (`ON_STOP`) o se cierra, libera todas las salidas activas; el firmware además tiene un watchdog de **1.5 s** (`HOLD_TIMEOUT_MS`).

**Seguridad (Clave AES):** Autenticación mediante clave compartida AES-CBC de 16, 24 o 32 caracteres (por defecto `abcdefghijklmnop`, debe coincidir con `AES_KEY` en `keys.h` del firmware).

## Requisitos del Sistema

- **minSdk**: 26 (Android 8.0 Oreo)
- **targetSdk**: 34 (Android 14)
- **compileSdk**: 34
- **Kotlin**: 1.9.20
- **Gradle**: 8.2
- **Android Gradle Plugin**: 8.1.4

## Dependencias Principales

- **Jetpack Compose BOM**: 2023.10.01
- **Material3** y **Material Icons Extended**
- **Activity Compose**: 1.8.1
- **Lifecycle & ViewModel**: 2.6.2
- **Core KTX**: 1.12.0
- **Kotlinx Coroutines**: 1.7.3
- **Accompanist Permissions**: 0.32.0

## Permisos

La aplicación solicita los siguientes permisos según la versión de Android:

### Android 12+ (API 31+)
- `BLUETOOTH_SCAN` (`neverForLocation`)
- `BLUETOOTH_CONNECT`
- `BLUETOOTH_ADVERTISE`

### Android < 12 (API < 31)
- `BLUETOOTH`
- `BLUETOOTH_ADMIN`
- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`

### Segundo plano, notificaciones y arranque
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_CONNECTED_DEVICE`
- `POST_NOTIFICATIONS` (solicitado en Android 13+ para mostrar la notificación del servicio)
- `RECEIVE_BOOT_COMPLETED`

## Uso de la Aplicación

### Primera Vez

1. **Otorgar Permisos**: La app solicitará los permisos de Bluetooth necesarios (y de notificaciones en Android 13+).
2. **Activar Bluetooth**: Si está desactivado, mostrará el botón para activarlo.
3. **Configurar Dispositivo**:
   - Tocar el ícono de configuración (⚙️) en la barra superior.
   - Ingresar la **Contraseña BLE (clave AES)** de 16, 24 o 32 caracteres (por defecto `abcdefghijklmnop`).
   - Ajustar si se desea el comportamiento en segundo plano (aplican al instante):
     - **Servicio en segundo plano**: mantiene la conexión al cerrar la app.
     - **Reconexión automática**: reconecta si se pierde la señal o se apaga el Bluetooth.
     - **Conexión automática**: conecta al abrir la app si hay un dispositivo guardado.
     - **Inicio automático**: inicia el servicio al encender el teléfono.
   - Presionar **Escanear**, seleccionar el dispositivo `AlarmaMoto` y tocar **Guardar**.

### Operación Normal

1. **Barra superior**: Muestra el estado general (`Conectado` / `Desconectado` y estado de bloqueo).
2. **Acciones (cuadrícula 2×3)**:
   - **Conectar / Desconectar**: Gestiona la conexión BLE con el dispositivo guardado.
   - **Bloquear / Desbloquear**: Alterna el relé principal (habilitado al estar conectado).
   - **Encender / Apagar**: Toggle para la salida de encendido (GPIO `14`).
   - **Arrancar** *(mantener)*: Activa el arranque (GPIO `12`) mientras se mantiene presionado; solo se habilita si la moto está conectada y con **Encender** activo.
   - **Botón 1** y **Botón 2** *(mantener)*: Salidas momentáneas auxiliares (GPIO `4`).
3. **Estado de la placa (`BoardStatus`)**: Muestra 6 indicadores en tiempo real (`Conectado`, `Bloqueado`, `Encendido`, `Arranque`, `Botón 1`, `Botón 2`).
4. **Widget**: Permite conectar/desconectar y bloquear/desbloquear directamente desde la pantalla de inicio.

## Arquitectura

### MVVM y Componentes Compartidos

- **Application (`AlarmaApplication`)**: Mantiene una única instancia compartida de `BleRepository` y `AppSettings` entre la UI, el `BleForegroundService` y el `AlarmaWidgetReceiver`.
- **Service (`BleForegroundService` + `BleServiceController`)**: Gestiona la conexión persistente, notificación, watchdog de conexión y reconexión con backoff.
- **ViewModel (`BleViewModel`)**: Expone `BleUiState` mediante `StateFlow` y coordina los temporizadores de keep-alive para los botones momentáneos.

```
UI (Compose) / Widget <-> BleServiceController / BleForegroundService <-> BleRepository <-> BleManager <-> ESP32
```

### Estado de UI (`BleUiState`)

```kotlin
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

- ✅ Escaneo de dispositivos BLE con filtro por UUID de servicio
- ✅ Conexión, desconexión y reconexión automática con backoff exponencial
- ✅ Servicio en primer plano (`connectedDevice`) con notificación persistente
- ✅ Inicio automático al arrancar el dispositivo (`BootReceiver`)
- ✅ Cifrado y descifrado AES-CBC (16/24/32 bytes) en comandos `RX` y notificaciones `STATUS`
- ✅ Control de bloqueo, encendido (toggle) y salidas momentáneas con keep-alive (`500 ms`)
- ✅ Panel en tiempo real `Estado de la placa` (6 indicadores)
- ✅ Widget de pantalla de inicio (1×2) sincronizado con la app
- ✅ UI reactiva con Jetpack Compose, Material Design 3 y `StateFlow`
- ✅ Gestión completa de permisos (Bluetooth + Notificaciones)

## Licencia

Este proyecto está bajo la licencia MIT.
