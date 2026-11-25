package com.example.alarmaapp.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.alarmaapp.AlarmaApplication
import com.example.alarmaapp.utils.PermissionsManager

/**
 * Inicia el servicio al encender el telefono si el usuario habilito
 * "Inicio automatico" y el servicio en segundo plano.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val app = context.applicationContext as AlarmaApplication
        val settings = app.appSettings.values.value

        if (!settings.backgroundServiceEnabled || !settings.autoStartEnabled) return
        if (!app.bleRepository.hasSavedDevice()) return

        if (!PermissionsManager.hasAllPermissions(context)) {
            Log.w(TAG, "Auto-inicio omitido: faltan permisos BLE")
            return
        }

        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, BleForegroundService::class.java)
                    .setAction(BleForegroundService.ACTION_CONNECT)
            )
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo iniciar el servicio al arrancar", e)
        }
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}
