package com.example.alarmaapp.service

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.alarmaapp.AlarmaApplication
import com.example.alarmaapp.ble.BleManager

/**
 * Punto unico para que la app inicie el servicio o caiga a la conexion
 * directa cuando el servicio en segundo plano esta deshabilitado.
 */
object BleServiceController {

    private const val TAG = "BleServiceController"

    fun connect(context: Context) {
        val app = app(context)
        if (!app.appSettings.values.value.backgroundServiceEnabled) {
            app.appSettings.setServiceDesired(false)
            app.bleRepository.connectToSavedDevice()
            return
        }
        startForegroundService(context, BleForegroundService.ACTION_CONNECT)
    }

    fun reconnect(context: Context) {
        val app = app(context)
        if (!app.appSettings.values.value.backgroundServiceEnabled) {
            app.appSettings.setServiceDesired(false)
            app.bleRepository.disconnect()
            app.bleRepository.connectToSavedDevice()
            return
        }
        startForegroundService(context, BleForegroundService.ACTION_RECONNECT)
    }

    fun disconnect(context: Context) {
        val app = app(context)
        if (BleForegroundService.isRunning) {
            startForegroundService(context, BleForegroundService.ACTION_DISCONNECT)
        } else {
            app.appSettings.setServiceDesired(false)
            app.bleRepository.disconnect()
        }
    }

    /** Adopta una conexion ya activa sin volver a conectar. */
    fun ensureRunning(context: Context) {
        val app = app(context)
        if (!app.appSettings.values.value.backgroundServiceEnabled) return
        if (BleForegroundService.isRunning) return

        val state = app.bleRepository.connectionState.value
        if (state == BleManager.ConnectionState.CONNECTED ||
            state == BleManager.ConnectionState.CONNECTING
        ) {
            startForegroundService(context, BleForegroundService.ACTION_ENSURE)
        }
    }

    /** Avisa al servicio de que cambio un ajuste (por ejemplo, reconexion). */
    fun notifySettingsChanged(context: Context) {
        if (BleForegroundService.isRunning) {
            startForegroundService(context, BleForegroundService.ACTION_SETTINGS_CHANGED)
        }
    }

    /** Republica la notificacion, por ejemplo tras conceder el permiso. */
    fun refreshNotification(context: Context) {
        if (BleForegroundService.isRunning) {
            startForegroundService(context, BleForegroundService.ACTION_REFRESH_NOTIFICATION)
        }
    }

    private fun startForegroundService(context: Context, action: String) {
        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, BleForegroundService::class.java).setAction(action)
            )
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo iniciar el servicio ($action)", e)
            fallback(context, action)
        }
    }

    private fun fallback(context: Context, action: String) {
        val app = app(context)
        app.appSettings.setServiceDesired(false)

        when (action) {
            BleForegroundService.ACTION_CONNECT ->
                app.bleRepository.connectToSavedDevice()

            BleForegroundService.ACTION_RECONNECT -> {
                app.bleRepository.disconnect()
                app.bleRepository.connectToSavedDevice()
            }

            BleForegroundService.ACTION_DISCONNECT ->
                app.bleRepository.disconnect()

            BleForegroundService.ACTION_TOGGLE_CONNECT -> {
                val state = app.bleRepository.connectionState.value
                if (state == BleManager.ConnectionState.CONNECTED ||
                    state == BleManager.ConnectionState.CONNECTING ||
                    state == BleManager.ConnectionState.DISCONNECTING
                ) {
                    app.bleRepository.disconnect()
                } else {
                    app.bleRepository.connectToSavedDevice()
                }
            }

            BleForegroundService.ACTION_TOGGLE_LOCK -> {
                if (app.bleRepository.connectionState.value ==
                    BleManager.ConnectionState.CONNECTED
                ) {
                    app.bleRepository.sendToggleCommand()
                }
            }

            else -> Unit
        }
    }

    private fun app(context: Context): AlarmaApplication =
        context.applicationContext as AlarmaApplication
}
