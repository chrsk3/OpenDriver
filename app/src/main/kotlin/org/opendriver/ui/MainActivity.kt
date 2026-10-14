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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.opendriver.core.calculator.ViabilityEngine
import org.opendriver.core.model.BubbleSize
import org.opendriver.core.model.FuelType
import org.opendriver.core.model.Platform
import org.opendriver.core.model.RideOffer
import org.opendriver.core.model.VehicleProfile
import org.opendriver.data.PreferencesRepository
import org.opendriver.service.FloatingOverlayService
import org.opendriver.service.OpenDriverAccessibilityService
import java.util.Locale
import kotlin.math.roundToInt

/** Telas navegáveis do app. */
enum class Screen { HOME, EDIT_CALC, DETAILS }

private val ACCENT = Color(0xFF00E676)
private val BG = Color(0xFF121212)
private val CARD = Color(0xFF1E1E1E)
private val CARD_INNER = Color(0xFF252525)
private val BORDER = Color(0xFF333333)
private const val WEEKS_PER_MONTH = 4.33

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
                    surface = Color(0xFF1A1A1A),
                    onSurface = Color.White
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = BG) {
                    var screen by remember { mutableStateOf(Screen.HOME) }
                    BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }

                    when (screen) {
                        Screen.HOME -> MainScreen(
                            repo = repo,
                            onEditCalc = { screen = Screen.EDIT_CALC },
                            onDetails = { screen = Screen.DETAILS },
                            onTestBubble = { simularCorrida() }
                        )
                        Screen.EDIT_CALC -> EditCalculationScreen(
                            repo = repo,
                            onBack = { screen = Screen.HOME },
                            onDetails = { screen = Screen.DETAILS }
                        )
                        Screen.DETAILS -> DetailsScreen(
                            repo = repo,
                            onBack = { screen = Screen.HOME },
                            onEdit = { screen = Screen.EDIT_CALC }
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

// ─── Utilitários ──────────────────────────────────────────────────────────────

fun Double.fmt2(): String = String.format(Locale.US, "%.2f", this)

fun Double.brl(): String = "R$ " + fmt2()

private fun String.toDoubleSafe(fallback: Double): Double =
    this.replace(",", ".").trim().toDoubleOrNull() ?: fallback

// ─── TELA PRINCIPAL ──────────────────────────────────────────────────────────

@Composable
fun MainScreen(
    repo: PreferencesRepository,
    onEditCalc: () -> Unit,
    onDetails: () -> Unit,
    onTestBubble: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var showCopilotSettings by remember { mutableStateOf(false) }
    var copilotEnabled by remember { mutableStateOf(repo.getCopilotEnabled()) }
    var earningsEnabled by remember { mutableStateOf(repo.getEarningsCalcEnabled()) }
    var hasOverlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var hasAccessibility by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }

    LaunchedEffect(showCopilotSettings) {
        if (!showCopilotSettings) {
            hasOverlay = Settings.canDrawOverlays(context)
            hasAccessibility = isAccessibilityServiceEnabled(context)
        }
    }

    // Mantém o botão flutuante em sincronia com o estado do Copiloto.
    LaunchedEffect(copilotEnabled) {
        if (copilotEnabled) FloatingOverlayService.enable(context) else FloatingOverlayService.hide(context)
    }

    if (showCopilotSettings) {
        CopilotSettingsDialog(
            repo = repo,
            hasOverlay = hasOverlay,
            hasAccessibility = hasAccessibility,
            onTestBubble = onTestBubble,
            onDismiss = { showCopilotSettings = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        AppHeader()

        Spacer(modifier = Modifier.height(12.dp))

        FeatureCard(
            iconBg = Color(0xFF1C3B2D),
            icon = { Icon(Icons.Default.MyLocation, null, tint = ACCENT, modifier = Modifier.size(26.dp)) },
            title = "Copiloto",
            subtitle = "Bolha flutuante de viabilidade em tempo real",
            isEnabled = copilotEnabled,
            onToggle = {
                copilotEnabled = it
                repo.setCopilotEnabled(it)
                // Copiloto é o switch mestre: liga mostra o botão flutuante; desliga remove tudo.
                if (it) FloatingOverlayService.enable(context) else FloatingOverlayService.hide(context)
            },
            onSettings = { showCopilotSettings = true }
        )

        Spacer(modifier = Modifier.height(10.dp))

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
            onSettings = onEditCalc
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Ações rápidas do Cálculo de Ganhos
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onEditCalc,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, ACCENT),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ACCENT)
            ) {
                Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Editar Cálculo", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = onDetails,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ACCENT)
            ) {
                Icon(Icons.Default.BarChart, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Ver Detalhes", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "v1.0.0 · OpenDriver Open Source",
            fontSize = 11.sp,
            color = Color(0xFF555555),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun AppHeader() {
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
                    .background(ACCENT),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.DirectionsCar, null, tint = Color.Black, modifier = Modifier.size(26.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text("OpenDriver", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Text("100% Open Source • Privacidade Total", fontSize = 11.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
private fun ScreenHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1A1A))
            .padding(horizontal = 8.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, null, tint = Color.White)
            }
            Spacer(Modifier.width(4.dp))
            Column {
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Text(subtitle, fontSize = 11.sp, color = Color.Gray)
            }
        }
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
        colors = CardDefaults.cardColors(containerColor = CARD),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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

            IconButton(onClick = onSettings) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Configurações",
                    tint = Color(0xFF888888),
                    modifier = Modifier.size(22.dp)
                )
            }

            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = ACCENT,
                    uncheckedThumbColor = Color.Gray,
                    uncheckedTrackColor = Color(0xFF333333)
                )
            )
        }
    }
}

// ─── TELA: EDITAR CÁLCULO ─────────────────────────────────────────────────────

@Composable
fun EditCalculationScreen(
    repo: PreferencesRepository,
    onBack: () -> Unit,
    onDetails: () -> Unit
) {
    val context = LocalContext.current

    // Rotina de trabalho
    var dias by remember { mutableIntStateOf(repo.getDiasPorSemana()) }
    var horasText by remember { mutableStateOf(repo.getHorasPorDia().fmt2()) }
    var kmText by remember { mutableStateOf(repo.getKmPorDia().fmt2()) }
    var metaText by remember { mutableStateOf(repo.getMetaSemanal().fmt2()) }

    // Veículo
    var vehicleType by remember { mutableStateOf(repo.getVehicleType()) }
    var vehicleSituation by remember { mutableStateOf(repo.getVehicleSituation()) }
    var placa by remember { mutableStateOf(repo.getVehiclePlate()) }

    // Categorias
    var categories by remember { mutableStateOf(repo.getSelectedCategories()) }

    // Custos mensais
    var financiamentoText by remember { mutableStateOf(repo.getFinanciamentoMensal().fmt2()) }
    var manutencaoText by remember { mutableStateOf(repo.getManutencaoMensal().fmt2()) }
    var seguroText by remember { mutableStateOf(repo.getSeguroMensal().fmt2()) }
    var outrosText by remember { mutableStateOf(repo.getOutrosCustosMensais().fmt2()) }
    var impostosText by remember { mutableStateOf(repo.getImpostosAnuais().fmt2()) }

    // Combustível / energia
    val profile = remember { repo.getVehicleProfile() }
    var fuelType by remember { mutableStateOf(profile.fuelType) }
    var fuelPriceText by remember { mutableStateOf(profile.fuelPrice.fmt2()) }
    var consumptionText by remember { mutableStateOf(profile.fuelConsumption.fmt2()) }
    var kwhText by remember { mutableStateOf(profile.kwhPer100Km.fmt2()) }
    var maintKmText by remember { mutableStateOf(profile.maintenanceCostPerKm.fmt2()) }
    var deprecKmText by remember { mutableStateOf(profile.depreciationCostPerKm.fmt2()) }
    var autonomiaText by remember { mutableStateOf(repo.getAutonomiaCarga().fmt2()) }
    var capacidadeText by remember { mutableStateOf(repo.getCapacidadeBateria().fmt2()) }

    // Metas do semáforo (definem a cor do card)
    val driverPrefs = remember { repo.getDriverPreferences() }
    var minNetKmText by remember { mutableStateOf(driverPrefs.minNetPerKm.fmt2()) }
    var minNetHoraText by remember { mutableStateOf(driverPrefs.minNetHourlyRate.fmt2()) }
    var minGrossKmText by remember { mutableStateOf(driverPrefs.minGrossPerKm.fmt2()) }
    var minNotaText by remember { mutableStateOf(driverPrefs.minPassengerRating.fmt2()) }

    val isElectric = fuelType == FuelType.ELECTRIC
    val scroll = rememberScrollState()

    val custoFixoMes = financiamentoText.toDoubleSafe(repo.getFinanciamentoMensal()) +
        manutencaoText.toDoubleSafe(repo.getManutencaoMensal()) +
        seguroText.toDoubleSafe(repo.getSeguroMensal()) +
        outrosText.toDoubleSafe(repo.getOutrosCustosMensais()) +
        impostosText.toDoubleSafe(repo.getImpostosAnuais()) / 12.0

    fun salvar() {
        val h = horasText.toDoubleSafe(repo.getHorasPorDia())
        val km = kmText.toDoubleSafe(repo.getKmPorDia())
        val meta = metaText.toDoubleSafe(repo.getMetaSemanal())
        val pPrice = fuelPriceText.toDoubleSafe(profile.fuelPrice)
        val pCons = consumptionText.toDoubleSafe(profile.fuelConsumption)
        val pKwh = kwhText.toDoubleSafe(profile.kwhPer100Km)
        val pMaint = maintKmText.toDoubleSafe(profile.maintenanceCostPerKm)
        val pDep = deprecKmText.toDoubleSafe(profile.depreciationCostPerKm)

        repo.setDiasPorSemana(dias)
        repo.setHorasPorDia(h)
        repo.setKmPorDia(km)
        repo.setMetaSemanal(meta)
        repo.setVehicleType(vehicleType)
        repo.setVehicleSituation(vehicleSituation)
        repo.setVehiclePlate(placa.uppercase().trim())
        repo.setSelectedCategories(categories)
        repo.setFinanciamentoMensal(financiamentoText.toDoubleSafe(0.0))
        repo.setManutencaoMensal(manutencaoText.toDoubleSafe(0.0))
        repo.setSeguroMensal(seguroText.toDoubleSafe(0.0))
        repo.setOutrosCustosMensais(outrosText.toDoubleSafe(0.0))
        repo.setImpostosAnuais(impostosText.toDoubleSafe(0.0))
        repo.setAutonomiaCarga(autonomiaText.toDoubleSafe(repo.getAutonomiaCarga()))
        repo.setCapacidadeBateria(capacidadeText.toDoubleSafe(repo.getCapacidadeBateria()))

        val diasMes = (dias * WEEKS_PER_MONTH).coerceAtLeast(1.0)
        repo.saveVehicleProfile(
            profile.copy(
                fuelType = fuelType,
                fuelPrice = pPrice,
                fuelConsumption = pCons,
                kwhPer100Km = pKwh,
                maintenanceCostPerKm = pMaint,
                depreciationCostPerKm = pDep,
                estimatedDailyKm = km,
                dailyFixedCost = custoFixoMes / diasMes
            )
        )

        // Metas do semáforo: definem a cor do card (verde/amarelo/vermelho).
        repo.saveDriverPreferences(
            driverPrefs.copy(
                minNetPerKm = minNetKmText.toDoubleSafe(driverPrefs.minNetPerKm),
                minNetHourlyRate = minNetHoraText.toDoubleSafe(driverPrefs.minNetHourlyRate),
                minGrossPerKm = minGrossKmText.toDoubleSafe(driverPrefs.minGrossPerKm),
                minPassengerRating = minNotaText.toDoubleSafe(driverPrefs.minPassengerRating)
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
    ) {
        ScreenHeader("Editar Cálculo", "Custos, rotina e metas do motorista", onBack)

        Column(modifier = Modifier.padding(16.dp)) {

            // ── Rotina de trabalho ──────────────────────────────────────────
            SectionTitle("Rotina de Trabalho")
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Dias por semana", fontSize = 14.sp, color = Color.White, modifier = Modifier.weight(1f))
                    Text("$dias", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ACCENT)
                }
                Slider(
                    value = dias.toFloat(),
                    onValueChange = { dias = it.roundToInt() },
                    valueRange = 1f..7f,
                    steps = 5,
                    colors = SliderDefaults.colors(
                        thumbColor = ACCENT, activeTrackColor = ACCENT, inactiveTrackColor = Color(0xFF444444)
                    )
                )
                Spacer(Modifier.height(8.dp))
                NumberField(horasText, { horasText = it }, "Horas por dia")
                Spacer(Modifier.height(8.dp))
                NumberField(kmText, { kmText = it }, "KM rodados por dia")
                Spacer(Modifier.height(8.dp))
                NumberField(metaText, { metaText = it }, "Meta semanal (R$)")
            }

            Spacer(Modifier.height(18.dp))

            // ── Veículo ─────────────────────────────────────────────────────
            SectionTitle("Veículo")
            SectionCard {
                FieldLabel("Tipo de veículo")
                SingleChoiceChips(
                    options = listOf("Carro", "Moto", "Bike"),
                    selected = vehicleType,
                    onSelect = { vehicleType = it }
                )
                Spacer(Modifier.height(14.dp))
                FieldLabel("Situação")
                SingleChoiceChips(
                    options = listOf("Financiado", "Próprio", "Alugado"),
                    selected = vehicleSituation,
                    onSelect = { vehicleSituation = it }
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = placa,
                    onValueChange = { placa = it.uppercase().take(8) },
                    label = { Text("Placa do veículo", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors()
                )
            }

            Spacer(Modifier.height(18.dp))

            // ── Categorias ──────────────────────────────────────────────────
            SectionTitle("Categorias que aceita")
            SectionCard {
                MultiChoiceChips(
                    options = listOf("Uber X", "Comfort", "Black", "99 Pop", "InDrive"),
                    selected = categories,
                    onToggle = { cat ->
                        categories = if (categories.contains(cat)) categories - cat else categories + cat
                    }
                )
            }

            Spacer(Modifier.height(18.dp))

            // ── Custos mensais ──────────────────────────────────────────────
            SectionTitle("Custos Mensais")
            SectionCard {
                NumberField(financiamentoText, { financiamentoText = it }, "Financiamento / Parcela (R$)")
                Spacer(Modifier.height(8.dp))
                NumberField(manutencaoText, { manutencaoText = it }, "Manutenção (R$)")
                Spacer(Modifier.height(8.dp))
                NumberField(seguroText, { seguroText = it }, "Seguro (R$)")
                Spacer(Modifier.height(8.dp))
                NumberField(outrosText, { outrosText = it }, "Outros custos (R$)")
                Spacer(Modifier.height(8.dp))
                NumberField(impostosText, { impostosText = it }, "Impostos / IPVA por ano (R$)")
                Spacer(Modifier.height(12.dp))
                SummaryStrip("Custo fixo mensal", custoFixoMes.brl(), Color(0xFF40C4FF))
            }

            Spacer(Modifier.height(18.dp))

            // ── Metas do semáforo (cor do card) ─────────────────────────────
            SectionTitle("Metas do Semáforo")
            SectionCard {
                Text(
                    "Definem a cor do card: 🟢 ao atingir, 🟡 perto, 🔴 abaixo.",
                    fontSize = 11.sp, color = Color.Gray
                )
                Spacer(Modifier.height(10.dp))
                NumberField(minNetKmText, { minNetKmText = it }, "Lucro líquido mínimo por km (🟢)")
                Spacer(Modifier.height(8.dp))
                NumberField(minNetHoraText, { minNetHoraText = it }, "Lucro líquido mínimo por hora (🟢)")
                Spacer(Modifier.height(8.dp))
                NumberField(minGrossKmText, { minGrossKmText = it }, "Ganho bruto mínimo por km (🟢)")
                Spacer(Modifier.height(8.dp))
                NumberField(minNotaText, { minNotaText = it }, "Nota mínima do passageiro")
            }

            Spacer(Modifier.height(18.dp))

            // ── Combustível / energia ───────────────────────────────────────
            SectionTitle(if (isElectric) "Energia" else "Combustível")
            SectionCard {
                SingleChoiceChips(
                    options = listOf("Gasolina", "Etanol", "GNV", "Elétrico"),
                    selected = fuelLabel(fuelType),
                    onSelect = { label ->
                        val novo = labelToFuel(label)
                        if (novo == FuelType.ELECTRIC && fuelType != FuelType.ELECTRIC) {
                            fuelPriceText = "1.20"; maintKmText = "0.08"
                        } else if (novo != FuelType.ELECTRIC && fuelType == FuelType.ELECTRIC) {
                            fuelPriceText = "5.85"; maintKmText = "0.20"
                        }
                        fuelType = novo
                    }
                )
                Spacer(Modifier.height(14.dp))

                NumberField(
                    fuelPriceText, { fuelPriceText = it },
                    if (isElectric) "Preço do kWh (R$/kWh)" else "Preço do combustível (R$/${fuelType.unitLabel})"
                )
                Spacer(Modifier.height(8.dp))

                if (isElectric) {
                    NumberField(kwhText, { kwhText = it }, "Consumo no painel (kWh/100km)")
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) { NumberField(autonomiaText, { autonomiaText = it }, "Autonomia (km)") }
                        Box(Modifier.weight(1f)) { NumberField(capacidadeText, { capacidadeText = it }, "Bateria (kWh)") }
                    }
                } else {
                    NumberField(consumptionText, { consumptionText = it }, "Consumo médio (km/${fuelType.unitLabel})")
                }
                Spacer(Modifier.height(8.dp))
                NumberField(maintKmText, { maintKmText = it }, "Manutenção por km (ex: 0.20)")
                Spacer(Modifier.height(8.dp))
                NumberField(deprecKmText, { deprecKmText = it }, "Depreciação por km (ex: 0.15)")

                // Prévia do custo por km
                val pPrice = fuelPriceText.toDoubleSafe(profile.fuelPrice)
                val pCons = consumptionText.toDoubleSafe(profile.fuelConsumption)
                val pKwh = kwhText.toDoubleSafe(profile.kwhPer100Km)
                val pMaint = maintKmText.toDoubleSafe(profile.maintenanceCostPerKm)
                val pDep = deprecKmText.toDoubleSafe(profile.depreciationCostPerKm)
                val energyKm = if (isElectric) (pKwh / 100.0) * pPrice else if (pCons > 0) pPrice / pCons else 0.0
                val totalKm = energyKm + pMaint + pDep

                Spacer(Modifier.height(12.dp))
                SummaryStrip(
                    label = if (isElectric) "⚡ Energia/km" else "⛽ Combustível/km",
                    value = energyKm.brl(),
                    color = Color(0xFF81C784)
                )
                Spacer(Modifier.height(6.dp))
                SummaryStrip("Custo operacional por km", totalKm.brl(), ACCENT)
            }

            Spacer(Modifier.height(22.dp))

            // ── Ações ───────────────────────────────────────────────────────
            Button(
                onClick = {
                    salvar()
                    Toast.makeText(context, "Cálculo salvo!", Toast.LENGTH_SHORT).show()
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = ACCENT),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Save, null, tint = Color.Black, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Salvar e Voltar", color = Color.Black, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(10.dp))

            OutlinedButton(
                onClick = onDetails,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, ACCENT),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ACCENT)
            ) {
                Icon(Icons.Default.BarChart, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Ver Detalhes do Cálculo", fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ─── TELA: VER DETALHES ───────────────────────────────────────────────────────

@Composable
fun DetailsScreen(
    repo: PreferencesRepository,
    onBack: () -> Unit,
    onEdit: () -> Unit
) {
    val profile = remember { repo.getVehicleProfile() }
    val dias = repo.getDiasPorSemana()
    val horas = repo.getHorasPorDia()
    val kmDia = repo.getKmPorDia()
    val metaSemanal = repo.getMetaSemanal()

    val custoFixoMes = repo.getFinanciamentoMensal() + repo.getManutencaoMensal() +
        repo.getSeguroMensal() + repo.getOutrosCustosMensais() + repo.getImpostosAnuais() / 12.0

    // Projeções mensais
    val semanasMes = WEEKS_PER_MONTH
    val kmMes = kmDia * dias * semanasMes
    val horasMes = horas * dias * semanasMes
    val custoVariavelKm = profile.variableCostPerKm
    val custoVariavelMes = custoVariavelKm * kmMes
    val custoTotalMes = custoVariavelMes + custoFixoMes
    val metaMes = metaSemanal * semanasMes
    val lucroProjetado = metaMes - custoTotalMes
    val custoHora = if (horasMes > 0) custoTotalMes / horasMes else 0.0
    val receitaHora = if (horasMes > 0) metaMes / horasMes else 0.0
    val breakEvenKm = profile.totalCostPerKm
    val metaGanhoKm = if (kmMes > 0) metaMes / kmMes else 0.0

    val scroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
    ) {
        ScreenHeader("Ver Detalhes", "Como chegamos aos números", onBack)

        Column(modifier = Modifier.padding(16.dp)) {

            // ── Cartão de destaque ──────────────────────────────────────────
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (lucroProjetado >= 0) Color(0xFF14291B) else Color(0xFF2A1414)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        "Lucro líquido projetado / mês",
                        fontSize = 12.sp,
                        color = if (lucroProjetado >= 0) Color(0xFF81C784) else Color(0xFFEF9A9A)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        lucroProjetado.brl(),
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (lucroProjetado >= 0) ACCENT else Color(0xFFEF5350)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Meta mensal ${metaMes.brl()} − custo total ${custoTotalMes.brl()}",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // ── Custo por km ────────────────────────────────────────────────
            SectionTitle("Custo por KM")
            SectionCard {
                val energyLabel = if (profile.fuelType == FuelType.ELECTRIC) "Energia (kWh)" else "Combustível (${profile.fuelType.displayName})"
                BreakdownRow(energyLabel, profile.fuelCostPerKm.brl())
                BreakdownRow("Manutenção", profile.maintenanceCostPerKm.brl())
                BreakdownRow("Depreciação", profile.depreciationCostPerKm.brl())
                BreakdownRow("Custo fixo rateado", profile.fixedCostPerKm.brl())
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = BORDER)
                Spacer(Modifier.height(8.dp))
                BreakdownRow("Custo total por KM", profile.totalCostPerKm.brl(), valueColor = ACCENT, bold = true)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Fixos rateados sobre ${kmDia.fmt2()} km/dia.",
                    fontSize = 10.sp, color = Color.Gray
                )
            }

            Spacer(Modifier.height(18.dp))

            // ── Projeção mensal ─────────────────────────────────────────────
            SectionTitle("Projeção Mensal")
            SectionCard {
                BreakdownRow("KM rodados no mês", "${kmMes.fmt2()} km")
                BreakdownRow("Horas trabalhadas no mês", "${horasMes.fmt2()} h")
                Spacer(Modifier.height(8.dp))
                BreakdownRow("Custo variável", custoVariavelMes.brl())
                BreakdownRow("Custo fixo", custoFixoMes.brl())
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = BORDER)
                Spacer(Modifier.height(8.dp))
                BreakdownRow("Custo total mensal", custoTotalMes.brl(), valueColor = Color(0xFFEF5350), bold = true)
            }

            Spacer(Modifier.height(18.dp))

            // ── Metas ───────────────────────────────────────────────────────
            SectionTitle("Metas e Rendimento")
            SectionCard {
                BreakdownRow("Meta semanal", metaSemanal.brl())
                BreakdownRow("Meta mensal", metaMes.brl())
                BreakdownRow("Ganho necessário / km", metaGanhoKm.brl())
                BreakdownRow("Break-even (custo/km)", breakEvenKm.brl())
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = BORDER)
                Spacer(Modifier.height(8.dp))
                BreakdownRow("Custo operacional / hora", custoHora.brl(), valueColor = Color(0xFFEF5350))
                BreakdownRow("Receita necessária / hora", receitaHora.brl(), valueColor = ACCENT, bold = true)
            }

            Spacer(Modifier.height(18.dp))

            // ── Perfil salvo ────────────────────────────────────────────────
            SectionTitle("Perfil de Trabalho")
            SectionCard {
                BreakdownRow("Veículo", "${repo.getVehicleType()} · ${repo.getVehicleSituation()}")
                if (repo.getVehiclePlate().isNotBlank()) {
                    BreakdownRow("Placa", repo.getVehiclePlate())
                }
                BreakdownRow("Rotina", "$dias dias/sem • ${horas.fmt2()} h/dia")
                BreakdownRow("Propulsão", profile.fuelType.displayName)
                if (profile.fuelType == FuelType.ELECTRIC) {
                    BreakdownRow("Autonomia", "${repo.getAutonomiaCarga().fmt2()} km")
                }
                val cats = repo.getSelectedCategories()
                BreakdownRow("Categorias", if (cats.isEmpty()) "Nenhuma" else cats.joinToString(", "))
            }

            Spacer(Modifier.height(22.dp))

            Button(
                onClick = onEdit,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = ACCENT),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Edit, null, tint = Color.Black, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Editar Cálculo", color = Color.Black, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ─── COMPONENTES DE UI ────────────────────────────────────────────────────────

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = ACCENT,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(bottom = 8.dp, start = 2.dp)
    )
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CARD),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun SummaryStrip(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CARD_INNER)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun BreakdownRow(
    label: String,
    value: String,
    valueColor: Color = Color.White,
    bold: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontSize = 13.sp,
            color = Color.Gray,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            fontSize = if (bold) 15.sp else 13.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
            color = valueColor,
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun SingleChoiceChips(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { opt ->
            val sel = opt == selected
            FilterChip(
                selected = sel,
                onClick = { onSelect(opt) },
                label = { Text(opt, fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ACCENT,
                    selectedLabelColor = Color.Black,
                    labelColor = Color.Gray
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = sel,
                    borderColor = BORDER,
                    selectedBorderColor = ACCENT,
                    borderWidth = 1.dp,
                    selectedBorderWidth = 1.dp
                )
            )
        }
    }
}

@Composable
private fun MultiChoiceChips(
    options: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { opt ->
            val sel = selected.contains(opt)
            FilterChip(
                selected = sel,
                onClick = { onToggle(opt) },
                label = { Text(opt, fontSize = 12.sp) },
                leadingIcon = if (sel) {
                    { Icon(Icons.Default.Check, null, tint = Color.Black, modifier = Modifier.size(16.dp)) }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ACCENT,
                    selectedLabelColor = Color.Black,
                    labelColor = Color.Gray
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = sel,
                    borderColor = BORDER,
                    selectedBorderColor = ACCENT,
                    borderWidth = 1.dp,
                    selectedBorderWidth = 1.dp
                )
            )
        }
    }
}

@Composable
private fun NumberField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        colors = fieldColors()
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = ACCENT,
    unfocusedBorderColor = BORDER,
    focusedLabelColor = ACCENT,
    unfocusedLabelColor = Color.Gray,
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    cursorColor = ACCENT
)

private fun fuelLabel(type: FuelType): String = when (type) {
    FuelType.GASOLINE -> "Gasolina"
    FuelType.ETHANOL -> "Etanol"
    FuelType.CNG -> "GNV"
    FuelType.ELECTRIC -> "Elétrico"
    FuelType.DIESEL -> "Diesel"
}

private fun labelToFuel(label: String): FuelType = when (label) {
    "Gasolina" -> FuelType.GASOLINE
    "Etanol" -> FuelType.ETHANOL
    "GNV" -> FuelType.CNG
    "Elétrico" -> FuelType.ELECTRIC
    "Diesel" -> FuelType.DIESEL
    else -> FuelType.GASOLINE
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
    var opacity by remember { mutableIntStateOf(repo.getFloatingButtonOpacity()) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CARD),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("Ajustes do Copiloto", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                Spacer(Modifier.height(20.dp))

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
                HorizontalDivider(color = Color(0xFF2C2C2C))
                Spacer(Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AspectRatio, null, tint = ACCENT, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Botão Flutuante", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Text("Tamanho do card e opacidade do botão", fontSize = 11.sp, color = Color.Gray)
                Spacer(Modifier.height(14.dp))

                Card(colors = CardDefaults.cardColors(containerColor = CARD_INNER), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ZoomOutMap, null, tint = ACCENT, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Tamanho do Card", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                Text("Escala borda, texto e espaçamento do resultado", fontSize = 11.sp, color = Color.Gray)
                            }
                            Text(bubbleSize.displayName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ACCENT)
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BubbleSize.entries.forEach { size ->
                                val selected = bubbleSize == size
                                OutlinedButton(
                                    onClick = {
                                        bubbleSize = size
                                        repo.setBubbleSize(size)
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (selected) ACCENT else Color.Transparent,
                                        contentColor = if (selected) Color.Black else Color.Gray
                                    ),
                                    border = BorderStroke(1.dp, if (selected) ACCENT else Color(0xFF444444)),
                                    contentPadding = PaddingValues(vertical = 6.dp)
                                ) {
                                    Text(size.displayName, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                Card(colors = CardDefaults.cardColors(containerColor = CARD_INNER), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Opacity, null, tint = ACCENT, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Opacidade do Botão", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.weight(1f))
                            Text("$opacity%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ACCENT)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("10%", fontSize = 11.sp, color = Color.Gray)
                            Slider(
                                value = opacity.toFloat(),
                                onValueChange = {
                                    opacity = it.toInt()
                                    repo.setFloatingButtonOpacity(opacity)
                                },
                                valueRange = 10f..100f,
                                steps = 8,
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = ACCENT, activeTrackColor = ACCENT, inactiveTrackColor = Color(0xFF444444)
                                )
                            )
                            Text("100%", fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Tempo em que o card fica visível
                var cardSeconds by remember { mutableStateOf(repo.getCardDurationSeconds().toString()) }
                Card(colors = CardDefaults.cardColors(containerColor = CARD_INNER), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Timer, null, tint = ACCENT, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Tempo do Card", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                Text("Quanto tempo o resultado fica na tela", fontSize = 11.sp, color = Color.Gray)
                            }
                            Text("${cardSeconds}s", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ACCENT)
                        }
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = cardSeconds,
                            onValueChange = { novo ->
                                val digits = novo.filter { it.isDigit() }.take(2)
                                cardSeconds = digits
                                digits.toIntOrNull()?.let { repo.setCardDurationSeconds(it) }
                            },
                            label = { Text("Segundos (2–60)", fontSize = 12.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = fieldColors()
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Diagnóstico do leitor (última captura de corrida)
                Card(colors = CardDefaults.cardColors(containerColor = CARD_INNER), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.BugReport, null, tint = ACCENT, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Diagnóstico do Leitor", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Eventos de corrida lidos: ${repo.getReaderEventCount()}",
                            fontSize = 11.sp,
                            color = ACCENT
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            repo.getLastCaptureInfo(),
                            fontSize = 11.sp,
                            color = Color(0xFFB0BEC5)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Captura completa em Android/media/org.opendriver/last_capture.txt",
                            fontSize = 10.sp,
                            color = Color(0xFF607D8B)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = { onTestBubble(); onDismiss() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = ACCENT),
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
            .background(CARD_INNER)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (isGranted) ACCENT else Color.Gray, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(title, fontSize = 13.sp, color = Color.White, modifier = Modifier.weight(1f))
        if (isGranted) {
            Icon(Icons.Default.CheckCircle, null, tint = ACCENT, modifier = Modifier.size(20.dp))
        } else {
            TextButton(
                onClick = onGrant,
                colors = ButtonDefaults.textButtonColors(contentColor = ACCENT)
            ) {
                Text("Ativar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    val enabled = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
    return enabled.any { it.id.contains(context.packageName) }
}
