package com.example.alarmaapp.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.alarmaapp.ui.theme.LockedRed
import com.example.alarmaapp.ui.theme.UnlockedGreen

private val InactiveButtonColor = Color(0xFF455A64)

/**
 * Boton de tipo toggle: cada pulsacion cambia el estado de una salida.
 * Se usa para "Encender" (GPIO 14).
 */
@Composable
fun ToggleActionButton(
    label: String,
    activeLabel: String,
    isActive: Boolean,
    isConnected: Boolean,
    icon: ImageVector,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = UnlockedGreen
) {
    Button(
        onClick = onToggle,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        enabled = isConnected,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isActive) activeColor else InactiveButtonColor,
            disabledContainerColor = Color.Gray
        ),
        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 8.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isActive) activeLabel else label,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White
            )
            Text(
                text = "presionar",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

/**
 * Boton momentaneo: envia "down" al presionar y "up" al soltar.
 * Mientras siga presionado, el ViewModel reenvia "down" periodicamente
 * y el firmware apaga la salida si deja de recibirlo.
 */
@Composable
fun HoldActionButton(
    label: String,
    activeLabel: String,
    isActive: Boolean,
    isConnected: Boolean,
    icon: ImageVector,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = LockedRed
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var wasPressed by remember { mutableStateOf(false) }

    // La placa tambien puede reportar el boton como activo (por ejemplo si
    // otro telefono lo esta usando), asi que el color combina ambas fuentes.
    val active = isPressed || isActive

    LaunchedEffect(isPressed) {
        if (isPressed) {
            wasPressed = true
            onPressStart()
        } else if (wasPressed) {
            wasPressed = false
            onPressEnd()
        }
    }

    // Si se pierde la conexion mientras el dedo sigue apoyado, cerramos el
    // estado local. El firmware tambien libera la salida por timeout.
    LaunchedEffect(isConnected) {
        if (!isConnected && wasPressed) {
            wasPressed = false
            onPressEnd()
        }
    }

    // Si el composable desaparece con el dedo apoyado (rotacion, cierre de
    // pantalla, perdida de conexion), liberamos la salida.
    DisposableEffect(Unit) {
        onDispose {
            if (wasPressed) {
                wasPressed = false
                onPressEnd()
            }
        }
    }

    Button(
        onClick = {},
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        enabled = isConnected,
        interactionSource = interactionSource,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) activeColor else InactiveButtonColor,
            disabledContainerColor = Color.Gray
        ),
        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 8.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (active) activeLabel else label,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White
            )
            Text(
                text = "mantener",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}
