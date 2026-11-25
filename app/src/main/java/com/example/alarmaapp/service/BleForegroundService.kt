package com.example.alarmaapp.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.alarmaapp.AlarmaApplication
import com.example.alarmaapp.MainActivity
import com.example.alarmaapp.R
import com.example.alarmaapp.ble.BleManager
import com.example.alarmaapp.repository.BleRepository
import com.example.alarmaapp.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Servicio en primer plano que mantiene viva la conexion BLE.
 *
 * Corre en el mismo proceso que la app, asi que comparte la unica instancia
 * de [BleRepository] creada por [AlarmaApplication]. No hay IPC ni una segunda
 * conexion: la Activity, el widget y el servicio ven el mismo GATT.
 *
 * La notificacion es solo informativa. Las acciones se ejecutan desde la app
 * o desde el widget; el servicio no expone botones en la notificacion.
 */
class BleForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var repository: BleRepository
    private lateinit var settings: AppSettings

    private var notificationJob: Job? = null
    private var connectionJob: Job? = null
    private var reconnectJob: Job? = null
    private var watchdogJob: Job? = null

    private var reconnectAttempt = 0
    private var foregroundStarted = false

    override fun onCreate() {
        super.onCreate()
        val app = application as AlarmaApplication
        repository = app.bleRepository
        settings = app.appSettings
        isRunning = true

        createNotificationChannel()
        observeState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        // Si el usuario desactivo el servicio, no lo promovemos. La unica
        // excepcion es ACTION_DISCONNECT: hay que dejar que cierre el GATT
        // aunque el ajuste ya este apagado.
        if (action != ACTION_DISCONNECT && !settings.values.value.backgroundServiceEnabled) {
            Log.i(TAG, "Servicio deshabilitado en ajustes; se detiene")
            stopSelfSafely(startId)
            return START_NOT_STICKY
        }

        // Android exige startForeground en pocos segundos, incluso si la
        // accion es desconectar y el servicio se va a detener enseguida.
        if (!promoteToForeground()) {
            stopSelfSafely(startId)
            return START_NOT_STICKY
        }

        when (action) {
            ACTION_CONNECT -> handleConnect(startId)
            ACTION_DISCONNECT -> {
                handleDisconnect()
                stopSelfSafely(startId)
                return START_NOT_STICKY
            }

            ACTION_RECONNECT -> handleReconnect(startId)
            ACTION_ENSURE -> handleEnsure()
            ACTION_TOGGLE_CONNECT -> {
                if (isActiveConnection()) {
                    handleDisconnect()
                    stopSelfSafely(startId)
                    return START_NOT_STICKY
                }
                handleConnect(startId)
            }

            ACTION_TOGGLE_LOCK -> handleToggleLock()
            ACTION_REFRESH_NOTIFICATION -> updateNotification()
            ACTION_SETTINGS_CHANGED -> handleSettingsChanged(startId)
            null -> handleStickyRestart(startId)
            else -> Log.w(TAG, "Accion desconocida: $action")
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        cancelReconnect()
        cancelWatchdog()
        serviceScope.cancel()

        // Si el servicio muere sin una desconexion explicita, cerramos el
        // GATT para no dejar una conexion huerfana sin notificacion.
        try {
            repository.disconnect()
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo cerrar el GATT al destruir el servicio", e)
        }

        // Al detener el servicio, la notificacion debe desaparecer. No basta
        // con stopSelf: la ultima actualizacion se publico como notificacion
        // normal y el sistema la deja en la bandeja.
        cancelNotification()

        super.onDestroy()
    }

    private fun cancelNotification() {
        try {
            getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo quitar la notificacion", e)
        }
    }

    // ---------------------------------------------------------------------
    // Acciones
    // ---------------------------------------------------------------------

    private fun handleConnect(startId: Int) {
        settings.setServiceDesired(true)
        reconnectAttempt = 0
        cancelReconnect()

        if (!repository.hasSavedDevice()) {
            Log.w(TAG, "No hay dispositivo guardado; se detiene el servicio")
            settings.setServiceDesired(false)
            stopSelfSafely(startId)
            return
        }

        if (!repository.isBluetoothEnabled()) {
            updateNotification()
            scheduleReconnectIfNeeded()
            return
        }

        when (repository.connectionState.value) {
            BleManager.ConnectionState.DISCONNECTED ->
                repository.connectToSavedDevice()

            BleManager.ConnectionState.CONNECTED ->
                updateNotification()

            else -> Unit
        }
    }

    private fun handleDisconnect() {
        settings.setServiceDesired(false)
        cancelReconnect()
        cancelWatchdog()
        repository.disconnect()
    }

    private fun handleReconnect(startId: Int) {
        settings.setServiceDesired(true)
        reconnectAttempt = 0
        cancelReconnect()
        cancelWatchdog()

        if (!repository.hasSavedDevice()) {
            Log.w(TAG, "No hay dispositivo guardado para reconectar")
            settings.setServiceDesired(false)
            stopSelfSafely(startId)
            return
        }

        repository.disconnect()
        if (repository.isBluetoothEnabled()) {
            repository.connectToSavedDevice()
        } else {
            updateNotification()
            scheduleReconnectIfNeeded()
        }
    }

    private fun handleEnsure() {
        settings.setServiceDesired(true)
        when (repository.connectionState.value) {
            BleManager.ConnectionState.CONNECTED,
            BleManager.ConnectionState.CONNECTING -> updateNotification()

            BleManager.ConnectionState.DISCONNECTED -> {
                if (repository.hasSavedDevice() && repository.isBluetoothEnabled()) {
                    repository.connectToSavedDevice()
                }
            }

            else -> Unit
        }
    }

    private fun handleToggleLock() {
        if (repository.connectionState.value == BleManager.ConnectionState.CONNECTED) {
            repository.sendToggleCommand()
        }
    }

    private fun handleSettingsChanged(startId: Int) {
        if (!settings.values.value.backgroundServiceEnabled) {
            handleDisconnect()
            stopSelfSafely(startId)
            return
        }

        if (!settings.values.value.reconnectEnabled) {
            cancelReconnect()
        } else if (settings.isServiceDesired() &&
            repository.connectionState.value == BleManager.ConnectionState.DISCONNECTED
        ) {
            scheduleReconnectIfNeeded()
        }

        updateNotification()
    }

    /**
     * El sistema reinicia el servicio con un intent nulo despues de matar el
     * proceso. Solo reconectamos si el usuario queria el servicio activo.
     */
    private fun handleStickyRestart(startId: Int) {
        if (!settings.isServiceDesired()) {
            Log.i(TAG, "Reinicio sticky sin servicio deseado; se detiene")
            stopSelfSafely(startId)
            return
        }

        if (!repository.hasSavedDevice()) {
            settings.setServiceDesired(false)
            stopSelfSafely(startId)
            return
        }

        if (!repository.isBluetoothEnabled()) {
            updateNotification()
            scheduleReconnectIfNeeded()
            return
        }

        if (repository.connectionState.value == BleManager.ConnectionState.DISCONNECTED) {
            repository.connectToSavedDevice()
        } else {
            updateNotification()
        }
    }

    // ---------------------------------------------------------------------
    // Observacion de estado, reconexion y watchdog
    // ---------------------------------------------------------------------

    private fun observeState() {
        notificationJob = serviceScope.launch {
            combine(
                repository.connectionState,
                repository.statusAlarm
            ) { _, _ -> Unit }.collect {
                if (foregroundStarted) updateNotification()
            }
        }

        connectionJob = serviceScope.launch {
            repository.connectionState.collect { state ->
                when (state) {
                    BleManager.ConnectionState.CONNECTED -> {
                        reconnectAttempt = 0
                        cancelReconnect()
                        cancelWatchdog()
                    }

                    BleManager.ConnectionState.CONNECTING -> {
                        cancelReconnect()
                        startWatchdog()
                    }

                    BleManager.ConnectionState.DISCONNECTED -> {
                        cancelWatchdog()
                        if (settings.isServiceDesired() &&
                            settings.values.value.reconnectEnabled
                        ) {
                            scheduleReconnectIfNeeded()
                        } else {
                            updateNotification()
                        }
                    }

                    BleManager.ConnectionState.DISCONNECTING -> Unit
                }
            }
        }
    }

    /**
     * Si el GATT se queda en CONNECTING mas de [CONNECT_TIMEOUT_MS], lo
     * cerramos. El observer de DISCONNECTED programa el siguiente intento.
     */
    private fun startWatchdog() {
        cancelWatchdog()
        watchdogJob = serviceScope.launch {
            delay(CONNECT_TIMEOUT_MS)
            if (repository.connectionState.value == BleManager.ConnectionState.CONNECTING) {
                Log.w(TAG, "Conexion BLE colgada; se reinicia")
                repository.disconnect()
            }
        }
    }

    /**
     * Reintenta con backoff mientras el servicio siga deseado y la
     * reconexion este habilitada. Si Bluetooth esta apagado espera sin
     * consumir intentos.
     */
    private fun scheduleReconnectIfNeeded() {
        if (!settings.isServiceDesired()) return
        if (!settings.values.value.reconnectEnabled) return
        if (reconnectJob?.isActive == true) return

        val state = repository.connectionState.value
        if (state == BleManager.ConnectionState.CONNECTED ||
            state == BleManager.ConnectionState.CONNECTING
        ) {
            return
        }

        reconnectJob = serviceScope.launch {
            while (isActive && settings.isServiceDesired() &&
                settings.values.value.reconnectEnabled
            ) {
                if (!repository.isBluetoothEnabled()) {
                    updateNotification()
                    delay(BLUETOOTH_RETRY_MS)
                    continue
                }

                val current = repository.connectionState.value
                if (current == BleManager.ConnectionState.CONNECTED ||
                    current == BleManager.ConnectionState.CONNECTING
                ) {
                    return@launch
                }

                val delayMs = RECONNECT_DELAYS[
                    minOf(reconnectAttempt, RECONNECT_DELAYS.lastIndex)
                ]
                updateNotification()
                delay(delayMs)

                if (!isActive || !settings.isServiceDesired() ||
                    !settings.values.value.reconnectEnabled
                ) {
                    return@launch
                }

                if (repository.connectionState.value != BleManager.ConnectionState.DISCONNECTED) {
                    return@launch
                }

                if (!repository.hasSavedDevice()) {
                    settings.setServiceDesired(false)
                    stopSelfSafely()
                    return@launch
                }

                reconnectAttempt++
                if (!repository.connectToSavedDevice()) {
                    // Otro intento ya esta en curso; esperamos al observer.
                    return@launch
                }
                return@launch
            }
        }
    }

    private fun cancelReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
    }

    private fun cancelWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = null
    }

    private fun isActiveConnection(): Boolean {
        val state = repository.connectionState.value
        return state == BleManager.ConnectionState.CONNECTED ||
            state == BleManager.ConnectionState.CONNECTING ||
            state == BleManager.ConnectionState.DISCONNECTING
    }

    // ---------------------------------------------------------------------
    // Notificacion
    // ---------------------------------------------------------------------

    private fun promoteToForeground(): Boolean {
        return try {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            } else {
                0
            }
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(),
                type
            )
            foregroundStarted = true
            true
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo iniciar el servicio en primer plano", e)
            settings.setServiceDesired(false)
            false
        }
    }

    private fun updateNotification() {
        if (!foregroundStarted) return
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, buildNotification())
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo actualizar la notificacion", e)
        }
    }

    private fun buildNotification(): Notification {
        val state = repository.connectionState.value
        val status = repository.statusAlarm.value
        val deviceName = repository.getSavedDeviceName()
            ?.takeIf { it.isNotBlank() }
            ?: getString(R.string.app_name)

        val text = when (state) {
            BleManager.ConnectionState.CONNECTED ->
                if (status.locked) {
                    getString(R.string.notification_connected_locked)
                } else {
                    getString(R.string.notification_connected_unlocked)
                }

            BleManager.ConnectionState.CONNECTING ->
                getString(R.string.notification_connecting)

            BleManager.ConnectionState.DISCONNECTING ->
                getString(R.string.notification_disconnecting)

            BleManager.ConnectionState.DISCONNECTED -> when {
                !repository.isBluetoothEnabled() ->
                    getString(R.string.notification_bluetooth_off)

                settings.isServiceDesired() &&
                    settings.values.value.reconnectEnabled ->
                    getString(R.string.notification_reconnecting)

                else -> getString(R.string.notification_disconnected)
            }
        }

        val contentIntent = PendingIntent.getActivity(
            this,
            REQUEST_CONTENT,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_alarma)
            .setContentTitle(deviceName)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_ble),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_ble_description)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    private fun stopSelfSafely(startId: Int? = null) {
        if (startId != null) {
            stopSelf(startId)
        } else {
            stopSelf()
        }
    }

    companion object {
        private const val TAG = "BleForegroundService"
        private const val CHANNEL_ID = "ble_connection"
        private const val NOTIFICATION_ID = 1001
        private const val REQUEST_CONTENT = 100

        private const val CONNECT_TIMEOUT_MS = 20_000L
        private const val BLUETOOTH_RETRY_MS = 30_000L
        private val RECONNECT_DELAYS = longArrayOf(3_000, 5_000, 10_000, 20_000, 30_000, 60_000)

        const val ACTION_CONNECT = "com.example.alarmaapp.action.CONNECT"
        const val ACTION_DISCONNECT = "com.example.alarmaapp.action.DISCONNECT"
        const val ACTION_RECONNECT = "com.example.alarmaapp.action.RECONNECT"
        const val ACTION_ENSURE = "com.example.alarmaapp.action.ENSURE"
        const val ACTION_TOGGLE_CONNECT = "com.example.alarmaapp.action.TOGGLE_CONNECT"
        const val ACTION_TOGGLE_LOCK = "com.example.alarmaapp.action.TOGGLE_LOCK"
        const val ACTION_REFRESH_NOTIFICATION = "com.example.alarmaapp.action.REFRESH_NOTIFICATION"
        const val ACTION_SETTINGS_CHANGED = "com.example.alarmaapp.action.SETTINGS_CHANGED"

        @Volatile
        var isRunning: Boolean = false
            private set
    }
}
