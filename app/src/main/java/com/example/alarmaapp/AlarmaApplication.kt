package com.example.alarmaapp

import android.app.Application
import android.util.Log
import com.example.alarmaapp.ble.BleConstants
import com.example.alarmaapp.repository.BleRepository
import com.example.alarmaapp.settings.AppSettings
import com.example.alarmaapp.widget.AlarmaWidgetReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class AlarmaApplication : Application() {

    // Unica instancia de BLE para toda la app (Activity, servicio y widget).
    lateinit var bleRepository: BleRepository
        private set

    lateinit var appSettings: AppSettings
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        appSettings = AppSettings(this)
        bleRepository = BleRepository(this, BleConstants.DEFAULT_KEY)
        observeBleStateForWidget()
        observeSettingsForWidget()
        // Refresca los PendingIntent del widget al abrir la app; asi se
        // reemplazan los que quedaron de una version anterior.
        updateWidgets()
    }

    private fun observeBleStateForWidget() {
        appScope.launch {
            combine(
                bleRepository.connectionState,
                bleRepository.statusAlarm
            ) { _, _ -> Unit }.collect {
                updateWidgets()
            }
        }
    }

    /**
     * El widget decide si sus PendingIntent apuntan al servicio o al receiver
     * privado segun el ajuste de segundo plano. Al cambiarlo hay que reconstruir
     * las vistas para que los toques sigan funcionando.
     */
    private fun observeSettingsForWidget() {
        appScope.launch {
            appSettings.values.collect {
                updateWidgets()
            }
        }
    }

    private fun updateWidgets() {
        try {
            AlarmaWidgetReceiver.updateAll(this)
        } catch (e: Exception) {
            // Si el widget se elimino mientras se actualizaba, no tumbar la app.
            Log.w("AlarmaApplication", "No se pudo actualizar el widget", e)
        }
    }
}
