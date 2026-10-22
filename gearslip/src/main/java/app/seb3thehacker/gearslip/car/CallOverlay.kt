package app.seb3thehacker.gearslip.car

import androidx.compose.foundation.shape.CircleShape
import app.seb3thehacker.gearslip.car.theme.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.seb3thehacker.gearslip.call.CallUiState
import app.seb3thehacker.gearslip.call.CarCalls

/**
 * Takes the whole car screen while a call is ringing, because nothing else matters more right
 * now. A call under way moves into the nav bar instead (see NavCallPill), so it covers nothing.
 */
@Composable
fun CallOverlay() {
    val state by CarCalls.state.collectAsState()
    val context = LocalContext.current.applicationContext

    when (val s = state) {
        is CallUiState.Ringing -> IncomingCall(s, onAnswer = { CarCalls.answer(context) }, onDecline = CarCalls::decline)
        else -> {}
    }
}

@Composable
private fun IncomingCall(s: CallUiState.Ringing, onAnswer: () -> Unit, onDecline: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Filled.Call,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Incoming call",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                s.caller.label,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(56.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                CallButton(Icons.Filled.Close, "Decline", MaterialTheme.colorScheme.error, enabled = s.canDecline, onClick = onDecline)
                CallButton(Icons.Filled.Call, "Answer", MaterialTheme.colorScheme.primary, enabled = true, onClick = onAnswer)
            }
            if (!s.canDecline) {
                Spacer(Modifier.height(20.dp))
                Text(
                    "Already ringing out loud - answer here, or let it ring.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CallButton(icon: ImageVector, label: String, color: Color, enabled: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        GsIconButton(
            icon, label, onClick,
            colors = GsColors(color, Color.White),
            enabled = enabled,
            size = 72.dp,
            iconSize = 30.dp,
            shape = CircleShape,
        )
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}
