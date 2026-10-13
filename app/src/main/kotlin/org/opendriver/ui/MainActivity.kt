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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.opendriver.core.calculator.ViabilityEngine
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
                    surface = Color(0xFF1E1E1E)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        repo = repo,
                        onSimulate = { simularCorrida() }
                    )
                }
            }
        }
    }

    private fun simularCorrida() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(
                this,
                "Por favor, ative a permissão de sobreposição primeiro!",
                Toast.LENGTH_LONG
            ).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            return
        }

        val profile = repo.getVehicleProfile()
        val prefs = repo.getDriverPreferences()

        val corridaSimulada = RideOffer(
            platform = Platform.UBER,
            category = "UberX",
            grossFare = 28.50,
            pickupDistanceKm = 1.5,
            tripDistanceKm = 8.2,
            pickupDurationMinutes = 4.0,
            tripDurationMinutes = 18.0,
            passengerRating = 4.93
        )

        val resultado = ViabilityEngine.evaluate(corridaSimulada, profile, prefs)
        FloatingOverlayService.showResult(this, resultado)

        Toast.makeText(this, "Bolha flutuante exibida na tela!", Toast.LENGTH_SHORT).show()
    }
}

fun Double.formatTwoDecimals(): String = String.format(Locale.US, "%.2f", this)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    repo: PreferencesRepository,
    onSimulate: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var hasAccessibilityPermission by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }

    val profile = remember { repo.getVehicleProfile() }
    var selectedFuelType by remember { mutableStateOf(profile.fuelType) }

    var fuelPriceText by remember { mutableStateOf(profile.fuelPrice.formatTwoDecimals()) }
    var consumptionText by remember { mutableStateOf(profile.fuelConsumption.formatTwoDecimals()) }
    var kwhText by remember { mutableStateOf(profile.kwhPer100Km.formatTwoDecimals()) }
    var maintText by remember { mutableStateOf(profile.maintenanceCostPerKm.formatTwoDecimals()) }
    var deprecText by remember { mutableStateOf(profile.depreciationCostPerKm.formatTwoDecimals()) }

    LaunchedEffect(Unit) {
        hasOverlayPermission = Settings.canDrawOverlays(context)
        hasAccessibilityPermission = isAccessibilityServiceEnabled(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        // Cabeçalho
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.DirectionsCar,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "OpenDriver",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "100% Open Source • Privacidade Total",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }

        // Permissões
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Permissões do Sistema",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(12.dp))

                PermissionItem(
                    title = "Janela Flutuante (Sobreposição)",
                    description = "Necessária para desenhar a bolha em cima da Uber/99",
                    isGranted = hasOverlayPermission,
                    onGrant = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                PermissionItem(
                    title = "Leitor de Tela (Acessibilidade)",
                    description = "Lê dados da corrida automaticamente ao tocar",
                    isGranted = hasAccessibilityPermission,
                    onGrant = {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        context.startActivity(intent)
                    }
                )
            }
        }

        // Configuração do Veículo
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Custos do Veículo",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Seletor de Tipo de Combustível / Energia
                Text(
                    text = "Tipo de Propulsão / Combustível:",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val tipos = listOf(
                        Triple(FuelType.GASOLINE, "Gasolina", "⛽"),
                        Triple(FuelType.ETHANOL, "Etanol", "🌽"),
                        Triple(FuelType.ELECTRIC, "Elétrico", "⚡"),
                        Triple(FuelType.CNG, "GNV", "💨")
                    )

                    tipos.forEach { (tipo, label, emoji) ->
                        val isSelected = selectedFuelType == tipo
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedFuelType = tipo
                                if (tipo == FuelType.ELECTRIC && fuelPriceText == "5.85") {
                                    fuelPriceText = "0.90"
                                    maintText = "0.08" // Elétrico tem menos manutenção
                                } else if (tipo != FuelType.ELECTRIC && fuelPriceText == "0.90") {
                                    fuelPriceText = "5.85"
                                    maintText = "0.20"
                                }
                            },
                            label = { Text("$emoji $label") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.Black
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                val isElectric = selectedFuelType == FuelType.ELECTRIC

                // Campo 1: Preço da Energia / Combustível
                OutlinedTextField(
                    value = fuelPriceText,
                    onValueChange = { fuelPriceText = it },
                    label = {
                        Text(if (isElectric) "Preço do kWh (R$/kWh)" else "Preço do Combustível (R$/litro ou m³)")
                    },
                    supportingText = {
                        if (isElectric) {
                            Text("Ex: R$ 0.85 em casa ou R$ 1.90 em eletroposto público", fontSize = 11.sp)
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Campo 2: Consumo (kWh/100km para elétrico vs km/l para combustão)
                if (isElectric) {
                    OutlinedTextField(
                        value = kwhText,
                        onValueChange = { kwhText = it },
                        label = { Text("Consumo no Painel (kWh a cada 100 km)") },
                        supportingText = {
                            Text("Ex: 13.0 kWh/100km (BYD Dolphin Mini, Dolphin, Ora 03)", fontSize = 11.sp)
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = consumptionText,
                        onValueChange = { consumptionText = it },
                        label = { Text("Consumo Médio (km/litro ou km/m³)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Campo 3: Manutenção preventiva
                OutlinedTextField(
                    value = maintText,
                    onValueChange = { maintText = it },
                    label = { Text("Manutenção por KM (R$/km)") },
                    supportingText = {
                        Text(if (isElectric) "Elétricos gastam menos: ~R$ 0.08/km (sem óleo/velas)" else "Combustão: pneus, óleo, freios (~R$ 0.20/km)", fontSize = 11.sp)
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Campo 4: Depreciação
                OutlinedTextField(
                    value = deprecText,
                    onValueChange = { deprecText = it },
                    label = { Text("Depreciação por KM (R$/km)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Cálculo em tempo real dos custos por KM
                val pPrice = fuelPriceText.toDoubleOrNull() ?: profile.fuelPrice
                val pCons = consumptionText.toDoubleOrNull() ?: profile.fuelConsumption
                val pKwh = kwhText.toDoubleOrNull() ?: profile.kwhPer100Km
                val pMaint = maintText.toDoubleOrNull() ?: profile.maintenanceCostPerKm
                val pDep = deprecText.toDoubleOrNull() ?: profile.depreciationCostPerKm

                val fuelCostKm = if (isElectric) {
                    if (pKwh > 0) (pKwh / 100.0) * pPrice else 0.0
                } else {
                    if (pCons > 0) pPrice / pCons else 0.0
                }
                val totalCostKm = fuelCostKm + pMaint + pDep

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF14291B)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = if (isElectric)
                                "⚡ Custo Energia/KM: R$ %.2f".format(Locale.US, fuelCostKm)
                            else
                                "⛽ Custo Combustível/KM: R$ %.2f".format(Locale.US, fuelCostKm),
                            fontSize = 13.sp,
                            color = Color(0xFF81C784)
                        )
                        Text(
                            text = "Custo Total Real por KM: R$ %.2f".format(Locale.US, totalCostKm),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        val updated = profile.copy(
                            fuelType = selectedFuelType,
                            fuelPrice = pPrice,
                            fuelConsumption = pCons,
                            kwhPer100Km = pKwh,
                            maintenanceCostPerKm = pMaint,
                            depreciationCostPerKm = pDep
                        )
                        repo.saveVehicleProfile(updated)
                        Toast.makeText(context, "Custos do veículo salvos com sucesso!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Salvar Custos")
                }
            }
        }

        // Botão de Demonstração
        Button(
            onClick = onSimulate,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.Black
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Testar Bolha Flutuante Agora",
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
fun PermissionItem(
    title: String,
    description: String,
    isGranted: Boolean,
    onGrant: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Text(
                text = description,
                fontSize = 11.sp,
                color = Color.Gray
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (isGranted) {
            AssistChip(
                onClick = {},
                label = { Text("Ativo", color = Color(0xFF00E676)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color(0xFF00E676),
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
        } else {
            Button(
                onClick = onGrant,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text("Ativar", fontSize = 12.sp, color = Color.White)
            }
        }
    }
}

fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
    return enabledServices.any { it.id.contains(context.packageName) }
}
