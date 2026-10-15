package org.opendriver.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.opendriver.data.PreferencesRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(repo: PreferencesRepository, onBack: () -> Unit) {
    val scroll = rememberScrollState()
    var entries by remember { mutableStateOf(repo.getHistory()) }
    val fmt = remember { SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR")) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Histórico de Corridas",
            onBack = onBack,
            trailing = {
                if (entries.isNotEmpty()) {
                    IconButton(onClick = { repo.clearHistory(); entries = emptyList() }) {
                        Icon(Icons.Default.Delete, "Limpar", tint = TEXT_MUTED)
                    }
                }
            }
        )

        if (entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Nenhuma corrida registrada ainda", fontSize = 14.sp, color = TEXT_MUTED)
                    Spacer(Modifier.height(6.dp))
                    Text("As corridas lidas pelo Copiloto aparecem aqui.", fontSize = 12.sp, color = Color(0xFF556577))
                }
            }
        } else {
            Column(modifier = Modifier.padding(16.dp).verticalScroll(scroll)) {
                Text("${entries.size} corrida(s)", fontSize = 12.sp, color = TEXT_MUTED)
                Spacer(Modifier.height(10.dp))
                entries.forEach { e ->
                    val col = statusColorFor(e.status)
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = CARD),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(6.dp, 40.dp).clip(RoundedCornerShape(3.dp)).background(col)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(e.platform, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Spacer(Modifier.width(8.dp))
                                    Text(e.status, fontSize = 11.sp, color = col, fontWeight = FontWeight.SemiBold)
                                }
                                Spacer(Modifier.height(2.dp))
                                val metricsText = if (e.durationMinutes > 0) {
                                    "R$ ${e.fare.fmt2()} · ${e.distanceKm.fmt2()} km · ${e.durationMinutes.toInt()} min · ${fmt.format(Date(e.timeMs))}"
                                } else {
                                    "R$ ${e.fare.fmt2()} · ${e.distanceKm.fmt2()} km · ${fmt.format(Date(e.timeMs))}"
                                }
                                Text(metricsText, fontSize = 11.sp, color = TEXT_MUTED)
                                if (!e.pickupAddress.isNullOrBlank() || !e.destinationAddress.isNullOrBlank()) {
                                    Spacer(Modifier.height(2.dp))
                                    val route = listOfNotNull(
                                        e.pickupAddress?.takeIf { it.isNotBlank() },
                                        e.destinationAddress?.takeIf { it.isNotBlank() }
                                    ).joinToString(" ➔ ")
                                    Text(route, fontSize = 10.sp, color = Color(0xFF90CAF9), maxLines = 1)
                                }
                            }
                            Text(
                                e.profit.brl(),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (e.profit >= 0) ACCENT else RED_SOFT
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

private fun statusColorFor(status: String): Color = when {
    status.contains("Excelente", true) || status.contains("Boa", true) -> ACCENT
    status.contains("Regular", true) -> YELLOW_SOFT
    else -> RED_SOFT
}
