package com.example.alarmaapp.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Preferencias de funcionamiento en segundo plano.
 *
 * Se guardan en el mismo archivo que usa BleRepository ("AlarmaAppPrefs"),
 * pero con claves propias para no mezclarlas con la configuracion del
 * dispositivo ni con la clave AES.
 */
class AppSettings(context: Context) {

    data class Values(
        val backgroundServiceEnabled: Boolean = true,
        val reconnectEnabled: Boolean = true,
        val autoConnectEnabled: Boolean = true,
        val autoStartEnabled: Boolean = false
    )

    private val prefs: SharedPreferences =
        context.getSharedPreferences("AlarmaAppPrefs", Context.MODE_PRIVATE)

    private val _values = MutableStateFlow(load())
    val values: StateFlow<Values> = _values.asStateFlow()

    fun setBackgroundServiceEnabled(enabled: Boolean) =
        update { it.copy(backgroundServiceEnabled = enabled) }

    fun setReconnectEnabled(enabled: Boolean) =
        update { it.copy(reconnectEnabled = enabled) }

    fun setAutoConnectEnabled(enabled: Boolean) =
        update { it.copy(autoConnectEnabled = enabled) }

    fun setAutoStartEnabled(enabled: Boolean) =
        update { it.copy(autoStartEnabled = enabled) }

    fun wasNotificationPermissionAsked(): Boolean =
        prefs.getBoolean(KEY_NOTIFICATION_ASKED, false)

    fun markNotificationPermissionAsked() {
        prefs.edit().putBoolean(KEY_NOTIFICATION_ASKED, true).apply()
    }

    /**
     * Indica si el servicio debe seguir vivo. Se guarda aparte de los ajustes
     * porque sobrevive al reinicio del proceso: START_STICKY lo consulta para
     * saber si debe reconectar o detenerse.
     */
    fun isServiceDesired(): Boolean = prefs.getBoolean(KEY_SERVICE_DESIRED, false)

    fun setServiceDesired(desired: Boolean) {
        prefs.edit().putBoolean(KEY_SERVICE_DESIRED, desired).apply()
    }

    private fun update(transform: (Values) -> Values) {
        val newValues = transform(_values.value)
        prefs.edit()
            .putBoolean(KEY_BACKGROUND_SERVICE, newValues.backgroundServiceEnabled)
            .putBoolean(KEY_RECONNECT, newValues.reconnectEnabled)
            .putBoolean(KEY_AUTO_CONNECT, newValues.autoConnectEnabled)
            .putBoolean(KEY_AUTO_START, newValues.autoStartEnabled)
            .apply()
        _values.value = newValues
    }

    private fun load(): Values = Values(
        backgroundServiceEnabled = prefs.getBoolean(KEY_BACKGROUND_SERVICE, true),
        reconnectEnabled = prefs.getBoolean(KEY_RECONNECT, true),
        autoConnectEnabled = prefs.getBoolean(KEY_AUTO_CONNECT, true),
        autoStartEnabled = prefs.getBoolean(KEY_AUTO_START, false)
    )

    private companion object {
        const val KEY_BACKGROUND_SERVICE = "background_service_enabled"
        const val KEY_RECONNECT = "reconnect_enabled"
        const val KEY_AUTO_CONNECT = "auto_connect_enabled"
        const val KEY_AUTO_START = "auto_start_enabled"
        const val KEY_NOTIFICATION_ASKED = "notification_permission_asked"
        const val KEY_SERVICE_DESIRED = "service_desired"
    }
}
