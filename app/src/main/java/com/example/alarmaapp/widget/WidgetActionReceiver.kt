package com.example.alarmaapp.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.alarmaapp.AlarmaApplication
import com.example.alarmaapp.ble.BleManager
import com.example.alarmaapp.service.BleServiceController

/**
 * Acciones del widget cuando el servicio en segundo plano esta deshabilitado.
 *
 * El receiver no es exportado: solo puede recibir los PendingIntent que crea
 * la propia app. Las acciones viejas y publicas del widget ya no existen.
 */
class WidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val repository = (context.applicationContext as AlarmaApplication).bleRepository

        when (intent.action) {
            ACTION_TOGGLE_CONNECT -> {
                val state = repository.connectionState.value
                if (state == BleManager.ConnectionState.CONNECTED ||
                    state == BleManager.ConnectionState.CONNECTING ||
                    state == BleManager.ConnectionState.DISCONNECTING
                ) {
                    BleServiceController.disconnect(context)
                } else {
                    BleServiceController.connect(context)
                }
            }

            ACTION_TOGGLE_LOCK -> {
                if (repository.connectionState.value == BleManager.ConnectionState.CONNECTED) {
                    repository.sendToggleCommand()
                }
            }
        }

        AlarmaWidgetReceiver.updateAll(context)
    }

    companion object {
        const val ACTION_TOGGLE_CONNECT = "com.example.alarmaapp.widget.PRIVATE_TOGGLE_CONNECT"
        const val ACTION_TOGGLE_LOCK = "com.example.alarmaapp.widget.PRIVATE_TOGGLE_LOCK"
    }
}
