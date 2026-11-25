package com.example.alarmaapp.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.alarmaapp.AlarmaApplication
import com.example.alarmaapp.MainActivity
import com.example.alarmaapp.R
import com.example.alarmaapp.ble.BleManager
import com.example.alarmaapp.service.BleForegroundService

/**
 * Widget compacto de 1 fila x 2 columnas con los dos botones de la app,
 * uno al lado del otro:
 *  - Conectar / Desconectar
 *  - Bloquear / Desbloquear
 *
 * Copia el estilo de la app: botones redondeados tipo píldora, icono encima
 * del texto y colores que indican la accion (verde conectar/desbloquear, rojo
 * lo contrario, gris cuando no se puede usar).
 *
 * Usa el mismo BleRepository de la Application, por lo que comparte la
 * conexion y envia el mismo comando AES ("1") que la app.
 *
 * Las acciones ya no son broadcasts publicos: si el servicio en segundo plano
 * esta activo, el toque va directo al servicio; si esta deshabilitado, va a un
 * receiver privado (exported=false) de la propia app.
 */
class AlarmaWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val views = buildViews(context)
        for (appWidgetId in appWidgetIds) {
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    companion object {
        private const val REQUEST_CONNECT = 1
        private const val REQUEST_LOCK = 2
        private const val REQUEST_OPEN_APP = 3

        /** Refresca todos los widgets activos con el estado BLE actual. */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, AlarmaWidgetReceiver::class.java)
            val appWidgetIds = manager.getAppWidgetIds(component)
            if (appWidgetIds.isEmpty()) return

            val views = buildViews(context)
            for (appWidgetId in appWidgetIds) {
                manager.updateAppWidget(appWidgetId, views)
            }
        }

        private fun buildViews(context: Context): RemoteViews {
            val app = context.applicationContext as AlarmaApplication
            val repository = app.bleRepository
            val useService = app.appSettings.values.value.backgroundServiceEnabled
            val state = repository.connectionState.value
            val connected = state == BleManager.ConnectionState.CONNECTED
            val connecting = state == BleManager.ConnectionState.CONNECTING
            val configured = repository.hasSavedDevice()
            val locked = repository.statusAlarm.value.locked

            val views = RemoteViews(context.packageName, R.layout.widget_alarma)

            // --- Boton Conectar / Desconectar (igual que ConnectButton) ---
            val connectText = when {
                connected -> "Desconectar"
                connecting -> "Conectando"
                !configured -> "Configurar"
                else -> "Conectar"
            }
            val connectPill = when {
                connected -> R.drawable.widget_pill_red
                connecting || !configured -> R.drawable.widget_pill_gray
                else -> R.drawable.widget_pill_green
            }
            val connectIcon = if (connected) {
                R.drawable.widget_switch_left
            } else {
                R.drawable.widget_switch_right
            }

            views.setTextViewText(R.id.widget_connect_text, connectText)
            views.setImageViewResource(R.id.widget_connect_icon, connectIcon)
            views.setInt(R.id.widget_connect_button, "setBackgroundResource", connectPill)
            views.setContentDescription(R.id.widget_connect_button, connectText)
            // Sin dispositivo guardado, el boton abre la app para configurarlo.
            views.setOnClickPendingIntent(
                R.id.widget_connect_button,
                if (configured) {
                    toggleConnectPendingIntent(context, useService)
                } else {
                    activityPendingIntent(context)
                }
            )

            // --- Boton Bloquear / Desbloquear (igual que LockButton) ---
            val lockText = if (locked) "Desbloquear" else "Bloquear"
            val lockPill = when {
                !connected -> R.drawable.widget_pill_gray
                locked -> R.drawable.widget_pill_green
                else -> R.drawable.widget_pill_red
            }
            val lockIcon = if (locked) {
                R.drawable.widget_lock_open
            } else {
                R.drawable.widget_lock
            }

            views.setTextViewText(R.id.widget_lock_text, lockText)
            views.setImageViewResource(R.id.widget_lock_icon, lockIcon)
            views.setInt(R.id.widget_lock_button, "setBackgroundResource", lockPill)
            views.setContentDescription(R.id.widget_lock_button, lockText)
            views.setOnClickPendingIntent(
                R.id.widget_lock_button,
                if (connected) {
                    toggleLockPendingIntent(context, useService)
                } else {
                    null // Deshabilitado hasta que haya conexion, igual que en la app.
                }
            )

            return views
        }

        private fun toggleConnectPendingIntent(
            context: Context,
            useService: Boolean
        ): PendingIntent {
            return if (useService) {
                foregroundServicePendingIntent(
                    context,
                    BleForegroundService.ACTION_TOGGLE_CONNECT,
                    REQUEST_CONNECT
                )
            } else {
                broadcastPendingIntent(
                    context,
                    WidgetActionReceiver.ACTION_TOGGLE_CONNECT,
                    REQUEST_CONNECT
                )
            }
        }

        private fun toggleLockPendingIntent(
            context: Context,
            useService: Boolean
        ): PendingIntent {
            return if (useService) {
                foregroundServicePendingIntent(
                    context,
                    BleForegroundService.ACTION_TOGGLE_LOCK,
                    REQUEST_LOCK
                )
            } else {
                broadcastPendingIntent(
                    context,
                    WidgetActionReceiver.ACTION_TOGGLE_LOCK,
                    REQUEST_LOCK
                )
            }
        }

        private fun foregroundServicePendingIntent(
            context: Context,
            action: String,
            requestCode: Int
        ): PendingIntent {
            val intent = Intent(context, BleForegroundService::class.java).setAction(action)
            return PendingIntent.getForegroundService(
                context,
                requestCode,
                intent,
                pendingFlags()
            )
        }

        private fun broadcastPendingIntent(
            context: Context,
            action: String,
            requestCode: Int
        ): PendingIntent {
            val intent = Intent(context, WidgetActionReceiver::class.java).setAction(action)
            return PendingIntent.getBroadcast(context, requestCode, intent, pendingFlags())
        }

        private fun activityPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
            return PendingIntent.getActivity(context, REQUEST_OPEN_APP, intent, pendingFlags())
        }

        private fun pendingFlags(): Int =
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    }
}
