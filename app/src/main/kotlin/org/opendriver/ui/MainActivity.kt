package org.opendriver.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.opendriver.core.calculator.ViabilityEngine
import org.opendriver.core.model.Platform
import org.opendriver.core.model.RideOffer
import org.opendriver.data.PreferencesRepository
import org.opendriver.service.FloatingOverlayService

/** Telas navegáveis do app. */
enum class Screen { HOME, COPILOT, EARNINGS, EDIT, DETAILS }

class MainActivity : ComponentActivity() {

    private lateinit var repo: PreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = PreferencesRepository(this)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = ACCENT,
                    background = BG,
                    surface = SURF,
                    onSurface = Color.White
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = BG) {
                    var screen by remember { mutableStateOf(Screen.HOME) }
                    BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }

                    when (screen) {
                        Screen.HOME -> HomeScreen(
                            repo = repo,
                            onOpenCopilot = { screen = Screen.COPILOT },
                            onOpenEarnings = { screen = Screen.EARNINGS }
                        )
                        Screen.COPILOT -> CopilotScreen(
                            repo = repo,
                            onBack = { screen = Screen.HOME }
                        )
                        Screen.EARNINGS -> EarningsScreen(
                            repo = repo,
                            onBack = { screen = Screen.HOME },
                            onDetails = { screen = Screen.DETAILS },
                            onEdit = { screen = Screen.EDIT },
                            onTestBubble = { simularCorrida() }
                        )
                        Screen.EDIT -> EditScreen(
                            repo = repo,
                            onBack = { screen = Screen.EARNINGS }
                        )
                        Screen.DETAILS -> DetailsScreen(
                            repo = repo,
                            onBack = { screen = Screen.EARNINGS },
                            onEdit = { screen = Screen.EDIT }
                        )
                    }
                }
            }
        }
    }

    private fun simularCorrida() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Ative a permissão de sobreposição primeiro!", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        val profile = repo.getVehicleProfile()
        val prefs = repo.getDriverPreferences()
        val corridaSimulada = RideOffer(
            platform = Platform.UBER, category = "UberX",
            grossFare = 28.50, pickupDistanceKm = 1.5, tripDistanceKm = 8.2,
            pickupDurationMinutes = 4.0, tripDurationMinutes = 18.0, passengerRating = 4.93
        )
        val resultado = ViabilityEngine.evaluate(corridaSimulada, profile, prefs)
        FloatingOverlayService.showResult(this, resultado)
        Toast.makeText(this, "Bolha flutuante exibida!", Toast.LENGTH_SHORT).show()
    }
}

// ─── TELA PRINCIPAL (HOME) ───────────────────────────────────────────────────

@Composable
fun HomeScreen(
    repo: PreferencesRepository,
    onOpenCopilot: () -> Unit,
    onOpenEarnings: () -> Unit
) {
    val context = LocalContext.current
    val scroll = rememberScrollState()

    var copilotEnabled by remember { mutableStateOf(repo.getCopilotEnabled()) }
    var earningsEnabled by remember { mutableStateOf(repo.getEarningsCalcEnabled()) }

    LaunchedEffect(copilotEnabled) {
        if (copilotEnabled) FloatingOverlayService.enable(context) else FloatingOverlayService.hide(context)
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(scroll)) {
        AppHeader()

        Spacer(Modifier.height(12.dp))

        FeatureCard(
            iconBg = Color(0xFF12261C),
            icon = { Icon(Icons.Default.MyLocation, null, tint = ACCENT, modifier = Modifier.size(26.dp)) },
            title = "Copiloto",
            subtitle = "Bolha flutuante de viabilidade em tempo real",
            isEnabled = copilotEnabled,
            onToggle = {
                copilotEnabled = it
                repo.setCopilotEnabled(it)
            },
            onSettings = onOpenCopilot
        )

        Spacer(Modifier.height(10.dp))

        FeatureCard(
            iconBg = Color(0xFF12202F),
            icon = { Icon(Icons.Default.AttachMoney, null, tint = Color(0xFF40C4FF), modifier = Modifier.size(26.dp)) },
            title = "Cálculo de Ganhos",
            subtitle = "Custos do veículo, metas e aparência do cartão",
            isEnabled = earningsEnabled,
            onToggle = {
                earningsEnabled = it
                repo.setEarningsCalcEnabled(it)
            },
            onSettings = onOpenEarnings
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = "v1.0.0 · OpenDriver Open Source",
            fontSize = 11.sp,
            color = Color(0xFF555555),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun AppHeader() {
    Box(
        modifier = Modifier.fillMaxWidth().background(SURF).padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(ACCENT),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.DirectionsCar, null, tint = Color.Black, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text("OpenDriver", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Text("100% Open Source • Privacidade Total", fontSize = 11.sp, color = TEXT_MUTED)
            }
        }
    }
}

@Composable
fun FeatureCard(
    iconBg: Color,
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onSettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = CARD),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(iconBg),
                contentAlignment = Alignment.Center
            ) { icon() }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(subtitle, fontSize = 11.sp, color = TEXT_MUTED)
            }

            IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, "Configurações", tint = TEXT_MUTED, modifier = Modifier.size(22.dp))
            }

            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White, checkedTrackColor = ACCENT,
                    uncheckedThumbColor = Color.Gray, uncheckedTrackColor = Color(0xFF333333)
                )
            )
        }
    }
}
