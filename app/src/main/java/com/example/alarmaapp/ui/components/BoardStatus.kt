package com.example.alarmaapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.alarmaapp.ui.theme.LockedRed
import com.example.alarmaapp.ui.theme.UnlockedGreen

/**
 * Muestra el array de estado que envia la placa:
 * [conectado, bloqueado, encendido, arranque, boton1, boton2]
 */
@Composable
fun BoardStatus(
    isConnected: Boolean,
    isLocked: Boolean,
    isPowerOn: Boolean,
    isStarterActive: Boolean,
    isButton1Active: Boolean,
    isButton2Active: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Estado de la placa",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StatusChip("Conectado", isConnected, Modifier.weight(1f))
            StatusChip("Bloqueado", isLocked, Modifier.weight(1f), activeColor = LockedRed)
            StatusChip("Encendido", isPowerOn, Modifier.weight(1f), activeColor = UnlockedGreen)
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StatusChip("Arranque", isStarterActive, Modifier.weight(1f), activeColor = LockedRed)
            StatusChip("Botón 1", isButton1Active, Modifier.weight(1f), activeColor = UnlockedGreen)
            StatusChip("Botón 2", isButton2Active, Modifier.weight(1f), activeColor = UnlockedGreen)
        }
    }
}

@Composable
private fun StatusChip(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    activeColor: Color = UnlockedGreen
) {
    val color = if (active) activeColor else Color(0xFF9E9E9E)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(vertical = 6.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
