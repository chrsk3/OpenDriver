package org.opendriver.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.opendriver.core.calculator.ViabilityEngine
import org.opendriver.core.model.BubbleSize
import org.opendriver.core.model.FuelType
import org.opendriver.core.model.Platform
import org.opendriver.core.model.RideOffer
import org.opendriver.data.PreferencesRepository
import org.opendriver.service.FloatingOverlayService
import org.opendriver.service.OpenDriverAccessibilityService
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var repo: PreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = PreferencesRepository(this)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF00E676),
                    background = Color(0xFF121212),
                    surface = Color(0xFF1A1A1A),
                    onSurface = Color.White
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(repo = repo, onTestBubble = { simularCorrida() })
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

fun Double.fmt2(): String = String.format(Locale.US, "%.2f", this)

// ─── TELA PRINCIPAL ──────────────────────────────────────────────────────────

@Composable
fun MainScreen(repo: PreferencesRepository, onTestBubble: () -> Unit) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var showCopilotSettings by remember { mutableStateOf(false) }
    var showEarningsSettings by remember { mutableStateOf(false) }

    var copilotEnabled by remember { mutableStateOf(repo.getCopilotEnabled()) }
    var earningsEnabled by remember { mutableStateOf(repo.getEarningsCalcEnabled()) }
    var hasOverlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var hasAccessibility by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }

    // Re-check ao abrir settings depois de conceder permissões
    LaunchedEffect(showCopilotSettings) {
        if (!showCopilotSettings) {
            hasOverlay = Settings.canDrawOverlays(context)
            hasAccessibility = isAccessibilityServiceEnabled(context)
        }
    }

    // ── Dialogs de configuração ─────────────────────────────────────────────
    if (showCopilotSettings) {
        CopilotSettingsDialog(
            repo = repo,
            hasOverlay = hasOverlay,
            hasAccessibility = hasAccessibility,
            onTestBubble = onTestBubble,
            onDismiss = { showCopilotSettings = false }
        )
    }

    if (showEarningsSettings) {
        EarningsSettingsDialog(
            repo = repo,
            onDismiss = { showEarningsSettings = false }
        )
    }

    // ── Layout principal ────────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        // Cabeçalho
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1A1A))
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00E676)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text("OpenDriver", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text("100% Open Source • Privacidade Total", fontSize = 11.sp, color = Color.Gray)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Card Copiloto ────────────────────────────────────────────────────
        FeatureCard(
            iconBg = Color(0xFF1C3B2D),
            icon = { Icon(Icons.Default.MyLocation, null, tint = Color(0xFF00E676), modifier = Modifier.size(26.dp)) },
            title = "Copiloto",
            subtitle = "Bolha flutuante de viabilidade em tempo real",
            isEnabled = copilotEnabled,
            onToggle = {
                copilotEnabled = it
                repo.setCopilotEnabled(it)
            },
            onSettings = { showCopilotSettings = true }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // ── Card Cálculo de Ganhos ───────────────────────────────────────────
        FeatureCard(
            iconBg = Color(0xFF1A2A3C),
            icon = { Icon(Icons.Default.AttachMoney, null, tint = Color(0xFF40C4FF), modifier = Modifier.size(26.dp)) },
            title = "Cálculo de Ganhos",
            subtitle = "Configure os custos do veículo e suas metas",
            isEnabled = earningsEnabled,
            onToggle = {
                earningsEnabled = it
                repo.setEarningsCalcEnabled(it)
            },
            onSettings = { showEarningsSettings = true }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Versão
        Text(
            text = "v1.0.0 · OpenDriver Open Source",
            fontSize = 11.sp,
            color = Color(0xFF555555),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(20.dp))
    }
}

// ─── CARD DE FEATURE ──────────────────────────────────────────────────────────

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
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Ícone
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(subtitle, fontSize = 11.sp, color = Color.Gray)
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Botão de configuração
            IconButton(onClick = onSettings) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Configurações",
                    tint = Color(0xFF888888),
                    modifier = Modifier.size(22.dp)
                )
            }

            // Toggle
            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF00E676),
                    uncheckedThumbColor = Color.Gray,
                    uncheckedTrackColor = Color(0xFF333333)
                )
            )
        }
    }
}

// ─── DIALOG: AJUSTES DO COPILOTO ─────────────────────────────────────────────

@Composable
fun CopilotSettingsDialog(
    repo: PreferencesRepository,
    hasOverlay: Boolean,
    hasAccessibility: Boolean,
    onTestBubble: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var bubbleSize by remember { mutableStateOf(repo.getBubbleSize()) }
    var opacity by remember { mutableIntStateOf(repo.getBubbleOpacity()) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {

                // Cabeçalho
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("Ajustes do Copiloto", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                Spacer(Modifier.height(20.dp))

                // Permissões
                Text("Permissões", fontSize = 13.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))

                PermissionRow(
                    icon = Icons.Default.Layers,
                    title = "Janela Flutuante",
                    isGranted = hasOverlay,
                    onGrant = {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                    }
                )
                Spacer(Modifier.height(8.dp))
                PermissionRow(
                    icon = Icons.Default.Accessibility,
                    title = "Serviço de Acessibilidade",
                    isGranted = hasAccessibility,
                    onGrant = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                )

                Spacer(Modifier.height(20.dp))
                Divider(color = Color(0xFF2C2C2C))
                Spacer(Modifier.height(16.dp))

                // Botão flutuante
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AspectRatio, null, tint = Color(0xFF00E676), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Botão Flutuante", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Text("Ajuste o tamanho e a transparência", fontSize = 11.sp, color = Color.Gray)
                Spacer(Modifier.height(14.dp))

                // Tamanho
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF252525)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ZoomOutMap, null, tint = Color(0xFF00E676), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Tamanho", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                Text("Escolha entre pequeno, médio ou grande", fontSize = 11.sp, color = Color.Gray)
                            }
                            Text(
                                bubbleSize.displayName,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00E676)
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            BubbleSize.entries.forEach { size ->
                                val selected = bubbleSize == size
                                OutlinedButton(
                                    onClick = {
                                        bubbleSize = size
                                        repo.setBubbleSize(size)
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (selected) Color(0xFF00E676) else Color.Transparent,
                                        contentColor = if (selected) Color.Black else Color.Gray
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (selected) Color(0xFF00E676) else Color(0xFF444444)
                                    ),
                                    contentPadding = PaddingValues(vertical = 6.dp)
                                ) {
                                    Text(size.displayName, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Opacidade
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF252525)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Opacity, null, tint = Color(0xFF00E676), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Opacidade",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "$opacity%",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00E676)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("10%", fontSize = 11.sp, color = Color.Gray)
                            Slider(
                                value = opacity.toFloat(),
                                onValueChange = {
                                    opacity = it.toInt()
                                    repo.setBubbleOpacity(opacity)
                                },
                                valueRange = 10f..100f,
                                steps = 8,
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF00E676),
                                    activeTrackColor = Color(0xFF00E676),
                                    inactiveTrackColor = Color(0xFF444444)
                                )
                            )
                            Text("100%", fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Testar bolha
                Button(
                    onClick = { onTestBubble(); onDismiss() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.Black)
                    Spacer(Modifier.width(8.dp))
                    Text("Testar Bolha Agora", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ─── DIALOG: AJUSTES DE CÁLCULO DE GANHOS ────────────────────────────────────

@Composable
fun EarningsSettingsDialog(repo: PreferencesRepository, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val profile = remember { repo.getVehicleProfile() }

    var selectedFuelType by remember { mutableStateOf(profile.fuelType) }
    var fuelPriceText by remember { mutableStateOf(profile.fuelPrice.fmt2()) }
    var consumptionText by remember { mutableStateOf(profile.fuelConsumption.fmt2()) }
    var kwhText by remember { mutableStateOf(profile.kwhPer100Km.fmt2()) }
    var maintText by remember { mutableStateOf(profile.maintenanceCostPerKm.fmt2()) }
    var deprecText by remember { mutableStateOf(profile.depreciationCostPerKm.fmt2()) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Cabeçalho
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("Custos do Veículo", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                Spacer(Modifier.height(16.dp))

                // Seletor de combustível
                Text("Tipo de Propulsão", fontSize = 12.sp, color = Color.Gray)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        Triple(FuelType.GASOLINE, "Gasolina", "⛽"),
                        Triple(FuelType.ETHANOL, "Etanol", "🌽"),
                        Triple(FuelType.ELECTRIC, "Elétrico", "⚡"),
                        Triple(FuelType.CNG, "GNV", "💨")
                    ).forEach { (tipo, label, emoji) ->
                        val sel = selectedFuelType == tipo
                        FilterChip(
                            selected = sel,
                            onClick = {
                                selectedFuelType = tipo
                                if (tipo == FuelType.ELECTRIC && fuelPriceText == "5.85") { fuelPriceText = "0.90"; maintText = "0.08" }
                                else if (tipo != FuelType.ELECTRIC && fuelPriceText == "0.90") { fuelPriceText = "5.85"; maintText = "0.20" }
                            },
                            label = { Text("$emoji $label", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF00E676),
                                selectedLabelColor = Color.Black
                            )
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                val isElectric = selectedFuelType == FuelType.ELECTRIC

                VehicleTextField(
                    value = fuelPriceText,
                    onValueChange = { fuelPriceText = it },
                    label = if (isElectric) "Preço do kWh (R$/kWh)" else "Preço do Combustível (R$/litro)"
                )
                Spacer(Modifier.height(8.dp))

                if (isElectric) {
                    VehicleTextField(value = kwhText, onValueChange = { kwhText = it }, label = "Consumo no Painel (kWh/100km)")
                } else {
                    VehicleTextField(value = consumptionText, onValueChange = { consumptionText = it }, label = "Consumo Médio (km/litro)")
                }
                Spacer(Modifier.height(8.dp))

                VehicleTextField(
                    value = maintText,
                    onValueChange = { maintText = it },
                    label = if (isElectric) "Manutenção/km (ex: 0.08)" else "Manutenção/km (ex: 0.20)"
                )
                Spacer(Modifier.height(8.dp))

                VehicleTextField(value = deprecText, onValueChange = { deprecText = it }, label = "Depreciação/km (ex: 0.15)")
                Spacer(Modifier.height(12.dp))

                // Resumo custo/km
                val pPrice = fuelPriceText.toDoubleOrNull() ?: profile.fuelPrice
                val pCons = consumptionText.toDoubleOrNull() ?: profile.fuelConsumption
                val pKwh = kwhText.toDoubleOrNull() ?: profile.kwhPer100Km
                val pMaint = maintText.toDoubleOrNull() ?: profile.maintenanceCostPerKm
                val pDep = deprecText.toDoubleOrNull() ?: profile.depreciationCostPerKm
                val energyCostKm = if (isElectric) (pKwh / 100.0) * pPrice else if (pCons > 0) pPrice / pCons else 0.0
                val totalKm = energyCostKm + pMaint + pDep

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF14291B)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            if (isElectric) "⚡ Energia/KM: R$ %.2f".format(Locale.US, energyCostKm)
                            else "⛽ Combustível/KM: R$ %.2f".format(Locale.US, energyCostKm),
                            fontSize = 12.sp, color = Color(0xFF81C784)
                        )
                        Text(
                            "Custo Total por KM: R$ %.2f".format(Locale.US, totalKm),
                            fontSize = 15.sp, fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E676)
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                Button(
                    onClick = {
                        repo.saveVehicleProfile(
                            profile.copy(
                                fuelType = selectedFuelType, fuelPrice = pPrice,
                                fuelConsumption = pCons, kwhPer100Km = pKwh,
                                maintenanceCostPerKm = pMaint, depreciationCostPerKm = pDep
                            )
                        )
                        Toast.makeText(context, "Custos salvos!", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Salvar e Fechar", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ─── COMPONENTES REUTILIZÁVEIS ────────────────────────────────────────────────

@Composable
fun PermissionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    isGranted: Boolean,
    onGrant: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF252525))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (isGranted) Color(0xFF00E676) else Color.Gray, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(title, fontSize = 13.sp, color = Color.White, modifier = Modifier.weight(1f))
        if (isGranted) {
            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF00E676), modifier = Modifier.size(20.dp))
        } else {
            TextButton(
                onClick = onGrant,
                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF00E676))
            ) {
                Text("Ativar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun VehicleTextField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFF00E676),
            unfocusedBorderColor = Color(0xFF333333),
            focusedLabelColor = Color(0xFF00E676),
            unfocusedLabelColor = Color.Gray,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = Color(0xFF00E676)
        )
    )
}

fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    val enabled = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
    return enabled.any { it.id.contains(context.packageName) }
}
