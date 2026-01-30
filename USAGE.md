# Guía de Uso de Alarma BLE App

Esta guía describe cómo usar la aplicación Android Alarma BLE para controlar tu sistema de alarma ESP32-C3.

## Índice

1. [Instalación](#instalación)
2. [Configuración Inicial](#configuración-inicial)
3. [Uso Diario](#uso-diario)
4. [Solución de Problemas](#solución-de-problemas)

## Instalación

### Desde Android Studio

1. Abre el proyecto en Android Studio
2. Conecta tu dispositivo Android o inicia un emulador
3. Presiona el botón "Run" (▶️) o ejecuta:
   ```bash
   ./gradlew installDebug
   ```

### Desde APK Pre-compilado

1. Habilita "Fuentes Desconocidas" en tu dispositivo Android
2. Descarga el archivo APK
3. Instala el APK
4. Abre la aplicación "Alarma BLE"

## Configuración Inicial

### Paso 1: Permisos de Bluetooth

Al abrir la aplicación por primera vez, se te solicitarán los permisos necesarios:

**Android 12 y superior:**
- Escanear dispositivos Bluetooth cercanos
- Conectarse a dispositivos Bluetooth
- Anunciar via Bluetooth

**Android 11 e inferiores:**
- Acceso a Bluetooth
- Acceso a ubicación (necesario para escaneo BLE)

**Acción requerida:** Presiona "Solicitar Permisos" y acepta todos los permisos.

### Paso 2: Activar Bluetooth

Si Bluetooth no está activado, la app lo detectará y mostrará un mensaje.

**Acción requerida:** Presiona "Activar Bluetooth" para habilitar Bluetooth en tu dispositivo.

### Paso 3: Configurar tu Dispositivo ESP32

1. **Encender el ESP32**: Asegúrate de que tu dispositivo ESP32-C3 esté encendido y en modo BLE.

2. **Abrir Configuración**:
   - Presiona el ícono de engranaje (⚙️) en la esquina superior izquierda
   - Se abrirá el diálogo de "Configuración"

3. **Escanear Dispositivos**:
   - Presiona el botón "Escanear"
   - La aplicación buscará dispositivos BLE cercanos durante 10 segundos
   - Verás un indicador de progreso girando

4. **Seleccionar Dispositivo**:
   - En la lista, aparecerán los dispositivos encontrados
   - Busca tu dispositivo ESP32-C3 (normalmente se llama "ESP32-C3")
   - Presiona sobre el dispositivo para seleccionarlo
   - El dispositivo seleccionado mostrará una marca de verificación (✓)

5. **Configurar Contraseña**:
   - En el campo "Contraseña BLE", ingresa la contraseña de tu dispositivo
   - Por defecto es: `123456`
   - Si has cambiado la contraseña en el ESP32, usa la nueva

6. **Guardar Configuración**:
   - Presiona el botón "Guardar"
   - La aplicación guardará la configuración y comenzará a conectarse

### Paso 4: Conexión Inicial

Después de guardar la configuración:
- La app intentará conectarse al dispositivo automáticamente
- En la esquina superior derecha verás:
  - "Conectando..." mientras se establece la conexión
  - "Conectado" con ícono verde ✓ cuando esté conectado
  - El estado actual: "Moto bloqueada." o "Moto desbloqueada."

## Uso Diario

### Abrir la Aplicación

1. Abre "Alarma BLE" desde tu lista de apps
2. La app se conectará automáticamente al dispositivo guardado
3. Espera a que aparezca el estado "Conectado" en la esquina superior derecha

### Controlar la Alarma

#### Bloquear/Desbloquear

**Pantalla Principal:**
- Botón circular grande en el centro
- **Rojo con candado cerrado (🔒)**: Sistema bloqueado
- **Verde con candado abierto (🔓)**: Sistema desbloqueado

**Para cambiar el estado:**
1. Asegúrate de estar conectado (ícono verde arriba)
2. Presiona el botón central
3. El comando se enviará al ESP32
4. El botón se actualizará con el nuevo estado

### Indicadores de Estado

#### Barra Superior Derecha

**Desconectado:**
- Ícono rojo ✗
- Texto: "Desconectado"

**Conectando:**
- Texto: "Conectando..."

**Conectado:**
- Ícono verde ✓
- Texto: Estado actual del dispositivo
  - "Moto bloqueada." cuando está bloqueado
  - "Moto desbloqueada." cuando está desbloqueado

### Cambiar de Dispositivo

Si quieres conectarte a otro dispositivo ESP32:

1. Presiona el ícono de configuración (⚙️)
2. Presiona "Escanear" para buscar dispositivos
3. Selecciona el nuevo dispositivo de la lista
4. Si es necesario, actualiza la contraseña
5. Presiona "Guardar"
6. La app se desconectará del dispositivo anterior y se conectará al nuevo

## Solución de Problemas

### La App No Encuentra mi Dispositivo

**Problema:** Al escanear, no aparece el ESP32-C3 en la lista.

**Soluciones:**
1. Verifica que el ESP32 esté encendido
2. Asegúrate de estar cerca del dispositivo (máximo 10 metros)
3. Verifica que el ESP32 esté en modo BLE y no conectado a otro dispositivo
4. Reinicia el ESP32
5. Verifica que Bluetooth esté activado en tu teléfono
6. Intenta escanear nuevamente

### No se Puede Conectar

**Problema:** La app encuentra el dispositivo pero no se conecta.

**Soluciones:**
1. Verifica que la contraseña sea correcta (por defecto: 123456)
2. Ve a Configuración de Bluetooth en Android y:
   - Busca el dispositivo ESP32-C3
   - Si aparece como "Vinculado", desvincula el dispositivo
   - Intenta conectar nuevamente desde la app
3. Reinicia Bluetooth:
   - Desactiva y reactiva Bluetooth en tu teléfono
   - Abre la app nuevamente
4. Reinicia el ESP32
5. Reinicia la aplicación

### La Conexión se Pierde Constantemente

**Problema:** La app se conecta pero se desconecta frecuentemente.

**Soluciones:**
1. Mantén el teléfono cerca del ESP32 (distancia menor a 5 metros)
2. Verifica que no haya muchos dispositivos Bluetooth en el área
3. Reinicia el ESP32
4. Verifica la alimentación del ESP32 (batería/fuente de poder)

### El Botón No Cambia el Estado

**Problema:** Presiono el botón pero el estado no cambia.

**Soluciones:**
1. Verifica que estés conectado (ícono verde arriba)
2. Espera unos segundos y observa si el estado se actualiza
3. Desconecta y reconecta:
   - Ve a configuración
   - Guarda nuevamente para forzar una reconexión
4. Verifica que el ESP32 esté funcionando correctamente

### Los Permisos Fueron Denegados

**Problema:** Denegué los permisos por error.

**Soluciones:**
1. Ve a Configuración de Android
2. Busca "Aplicaciones"
3. Encuentra "Alarma BLE"
4. Presiona en "Permisos"
5. Habilita todos los permisos de Bluetooth y Ubicación
6. Cierra y vuelve a abrir la aplicación

### Bluetooth No se Activa

**Problema:** La app pide activar Bluetooth pero no sucede nada.

**Soluciones:**
1. Activa Bluetooth manualmente desde Configuración de Android
2. Reinicia tu teléfono
3. Verifica que tu dispositivo tenga Bluetooth LE compatible
4. Abre la aplicación nuevamente

## Funciones Avanzadas

### Reconexión Automática

La aplicación está configurada para:
- Recordar el último dispositivo conectado
- Conectarse automáticamente al abrir la app
- Intentar reconectar si se pierde la conexión temporalmente

**Nota:** Si el dispositivo no está disponible, la app mostrará "Desconectado" y seguirá intentando hasta que esté disponible o la cierres.

### Cambio de Contraseña

Si cambiaste la contraseña en tu ESP32:

1. Abre configuración (⚙️)
2. Actualiza el campo "Contraseña BLE" con la nueva contraseña
3. Presiona "Guardar"
4. La app usará la nueva contraseña para futuras conexiones

## Consejos de Uso

### Maximizar el Alcance
- Mantén el teléfono con línea de vista al ESP32
- Evita paredes gruesas o metal entre el teléfono y el dispositivo
- El alcance típico es de 5-10 metros en interiores

### Ahorrar Batería
- Cierra la app cuando no la estés usando
- No dejes el escaneo activo por períodos prolongados

### Seguridad
- Cambia la contraseña por defecto (123456) en el ESP32
- No compartas tu contraseña con personas no autorizadas
- La conexión BLE tiene cifrado, pero la contraseña es estática

## Contacto y Soporte

Si tienes problemas que no se resuelven con esta guía:
1. Verifica que tu ESP32 funcione correctamente
2. Asegúrate de tener la última versión de la app
3. Revisa los logs de Android Studio si eres desarrollador

---

**Versión de la Guía:** 1.0  
**Fecha:** Enero 2026  
**Compatible con:** Android 8.0 (API 26) y superior
