package org.opendriver.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import org.opendriver.core.model.Platform
import org.opendriver.core.model.RideOffer
import org.opendriver.core.model.VehicleProfile
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
    var fuelPriceText by remember { mutableStateOf(profile.fuelPrice.toString()) }
    var consumptionText by remember { mutableStateOf(profile.fuelConsumption.toString()) }
    var maintText by remember { mutableStateOf(profile.maintenanceCostPerKm.toString()) }
    var deprecText by remember { mutableStateOf(profile.depreciationCostPerKm.toString()) }

    // Atualiza status ao retomar tela
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

                // Permissão 1: Janela Flutuante
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

                // Permissão 2: Acessibilidade
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
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = fuelPriceText,
                    onValueChange = { fuelPriceText = it },
                    label = { Text("Preço do Combustível (R$/litro)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = consumptionText,
                    onValueChange = { consumptionText = it },
                    label = { Text("Consumo Médio (km/litro)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = maintText,
                    onValueChange = { maintText = it },
                    label = { Text("Manutenção por KM (R$/km - ex: 0.20)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = deprecText,
                    onValueChange = { deprecText = it },
                    label = { Text("Depreciação por KM (R$/km - ex: 0.15)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Resumo do custo/km calculado
                val pPrice = fuelPriceText.toDoubleOrNull() ?: profile.fuelPrice
                val pCons = consumptionText.toDoubleOrNull() ?: profile.fuelConsumption
                val pMaint = maintText.toDoubleOrNull() ?: profile.maintenanceCostPerKm
                val pDep = deprecText.toDoubleOrNull() ?: profile.depreciationCostPerKm
                val costKm = (if (pCons > 0) pPrice / pCons else 0.0) + pMaint + pDep

                Text(
                    text = "Custo Estimado por KM: R$ %.2f".format(Locale.US, costKm),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 15.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        val updated = profile.copy(
                            fuelPrice = pPrice,
                            fuelConsumption = pCons,
                            maintenanceCostPerKm = pMaint,
                            depreciationCostPerKm = pDep
                        )
                        repo.saveVehicleProfile(updated)
                        Toast.makeText(context, "Custos salvos com sucesso!", Toast.LENGTH_SHORT).show()
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
    val serviceName = "${context.packageName}/${OpenDriverAccessibilityService::class.java.name}"
    return enabledServices.any { it.id.contains(context.packageName) }
}
