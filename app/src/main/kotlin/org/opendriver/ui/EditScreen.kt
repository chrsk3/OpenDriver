package org.opendriver.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.opendriver.core.model.FuelType
import org.opendriver.data.PreferencesRepository
import java.util.Locale

private val CATEGORIES = listOf("Uber X", "Comfort", "Black", "99 Pop", "99 Plus", "InDrive", "Taxi", "Electric PRO", "Entregas")

@Composable
fun EditScreen(repo: PreferencesRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val scroll = rememberScrollState()
    val profile = remember { repo.getVehicleProfile() }

    var dias by remember { mutableIntStateOf(repo.getDiasPorSemana()) }
    var horasText by remember { mutableStateOf(repo.getHorasPorDia().fmt2()) }
    var kmText by remember { mutableStateOf(repo.getKmPorDia().fmt2()) }
    var metaText by remember { mutableStateOf(repo.getMetaSemanal().fmt2()) }

    var vehicleType by remember { mutableStateOf(repo.getVehicleType()) }
    var situation by remember { mutableStateOf(repo.getVehicleSituation()) }
    var placa by remember { mutableStateOf(repo.getVehiclePlate()) }
    var categories by remember { mutableStateOf(repo.getSelectedCategories()) }

    var financiamento by remember { mutableStateOf(repo.getFinanciamentoMensal().fmt2()) }
    var manutencao by remember { mutableStateOf(repo.getManutencaoMensal().fmt2()) }
    var seguro by remember { mutableStateOf(repo.getSeguroMensal().fmt2()) }
    var outros by remember { mutableStateOf(repo.getOutrosCustosMensais().fmt2()) }
    var impostos by remember { mutableStateOf(repo.getImpostosAnuais().fmt2()) }

    var fuelType by remember { mutableStateOf(profile.fuelType) }
    var fuelPrice by remember { mutableStateOf(profile.fuelPrice.fmt2()) }
    var consumption by remember { mutableStateOf(profile.fuelConsumption.fmt2()) }
    var kwh by remember { mutableStateOf(profile.kwhPer100Km.fmt2()) }
    var maintKm by remember { mutableStateOf(profile.maintenanceCostPerKm.fmt2()) }
    var deprecKm by remember { mutableStateOf(profile.depreciationCostPerKm.fmt2()) }
    var autonomia by remember { mutableStateOf(repo.getAutonomiaCarga().fmt2()) }
    var capacidade by remember { mutableStateOf(repo.getCapacidadeBateria().fmt2()) }

    val isElectric = fuelType == FuelType.ELECTRIC

    val custoFixoMes = financiamento.toDoubleSafe(0.0) + manutencao.toDoubleSafe(0.0) +
        seguro.toDoubleSafe(0.0) + outros.toDoubleSafe(0.0) + impostos.toDoubleSafe(0.0) / 12.0

    // Prévia do custo por km (mesma conta do motor)
    val previewKm = run {
        val pPrice = fuelPrice.toDoubleSafe(profile.fuelPrice)
        val pCons = consumption.toDoubleSafe(profile.fuelConsumption)
        val pKwh = kwh.toDoubleSafe(profile.kwhPer100Km)
        val pMaint = maintKm.toDoubleSafe(profile.maintenanceCostPerKm)
        val pDep = deprecKm.toDoubleSafe(profile.depreciationCostPerKm)
        val energy = if (isElectric) (pKwh / 100.0) * pPrice else if (pCons > 0) pPrice / pCons else 0.0
        energy + pMaint + pDep
    }

    fun salvar() {
        val km = kmText.toDoubleSafe(repo.getKmPorDia())
        repo.setDiasPorSemana(dias)
        repo.setHorasPorDia(horasText.toDoubleSafe(repo.getHorasPorDia()))
        repo.setKmPorDia(km)
        repo.setMetaSemanal(metaText.toDoubleSafe(repo.getMetaSemanal()))
        repo.setVehicleType(vehicleType)
        repo.setVehicleSituation(situation)
        repo.setVehiclePlate(placa.uppercase().trim())
        repo.setSelectedCategories(categories)
        repo.setFinanciamentoMensal(financiamento.toDoubleSafe(0.0))
        repo.setManutencaoMensal(manutencao.toDoubleSafe(0.0))
        repo.setSeguroMensal(seguro.toDoubleSafe(0.0))
        repo.setOutrosCustosMensais(outros.toDoubleSafe(0.0))
        repo.setImpostosAnuais(impostos.toDoubleSafe(0.0))
        repo.setAutonomiaCarga(autonomia.toDoubleSafe(repo.getAutonomiaCarga()))
        repo.setCapacidadeBateria(capacidade.toDoubleSafe(repo.getCapacidadeBateria()))

        val diasMes = (dias * 4.33).coerceAtLeast(1.0)
        repo.saveVehicleProfile(
            profile.copy(
                fuelType = fuelType,
                fuelPrice = fuelPrice.toDoubleSafe(profile.fuelPrice),
                fuelConsumption = consumption.toDoubleSafe(profile.fuelConsumption),
                kwhPer100Km = kwh.toDoubleSafe(profile.kwhPer100Km),
                maintenanceCostPerKm = maintKm.toDoubleSafe(profile.maintenanceCostPerKm),
                depreciationCostPerKm = deprecKm.toDoubleSafe(profile.depreciationCostPerKm),
                estimatedDailyKm = km,
                dailyFixedCost = custoFixoMes / diasMes
            )
        )
        Toast.makeText(context, "Cálculo salvo!", Toast.LENGTH_SHORT).show()
        onBack()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Editar Cálculo",
            onBack = onBack,
            trailing = {
                TextButton(onClick = { onBack() }) {
                    Icon(Icons.Default.RestartAlt, null, tint = ACCENT, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("RESETAR", color = ACCENT, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        )

        Box(modifier = Modifier.weight(1f)) {
            Column(modifier = Modifier.padding(16.dp).verticalScroll(scroll)) {

                // ── Informações de Trabalho ────────────────────────────────
                SectionTitle("Informações de Trabalho")
                SectionCard {
                    Text("Dias trabalhados por semana", fontSize = 12.sp, color = TEXT_MUTED)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..7).forEach { d ->
                            val sel = dias == d
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (sel) ACCENT else CARD_INNER)
                                    .clickable { dias = d }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("$d", fontSize = 13.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, color = if (sel) Color.Black else Color.White)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) { NumberField(horasText, { horasText = it }, "Horas/dia (h)") }
                        Box(Modifier.weight(1f)) { NumberField(kmText, { kmText = it }, "Km rodados/dia") }
                    }
                    Spacer(Modifier.height(8.dp))
                    NumberField(metaText, { metaText = it }, "Meta semanal (R$)")
                }

                Spacer(Modifier.height(18.dp))

                // ── Informações do Veículo ─────────────────────────────────
                SectionTitle("Informações do Veículo")
                Text("Tipo de Veículo", fontSize = 12.sp, color = TEXT_MUTED)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VehicleTypeOption("Carro", vehicleType) { vehicleType = it }
                    VehicleTypeOption("Moto", vehicleType) { vehicleType = it }
                    VehicleTypeOption("Bicicleta", vehicleType) { vehicleType = it }
                }

                Spacer(Modifier.height(14.dp))
                Text("Situação do Veículo", fontSize = 12.sp, color = TEXT_MUTED)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SituationOption("Financiado", situation, Modifier.weight(1f)) { situation = it }
                    SituationOption("Próprio", situation, Modifier.weight(1f)) { situation = it }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SituationOption("Alugado", situation, Modifier.weight(1f)) { situation = it }
                    SituationOption("Aluguel Privado", situation, Modifier.weight(1f)) { situation = it }
                }

                Spacer(Modifier.height(14.dp))
                Text("Placa", fontSize = 12.sp, color = TEXT_MUTED)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = placa,
                    onValueChange = { placa = it.uppercase().take(8) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = fieldColors()
                )

                Spacer(Modifier.height(14.dp))
                Text("Categorias", fontSize = 12.sp, color = TEXT_MUTED)
                Spacer(Modifier.height(8.dp))
                MultiChoiceChips(
                    options = CATEGORIES,
                    selected = categories,
                    onToggle = { c -> categories = if (categories.contains(c)) categories - c else categories + c }
                )

                Spacer(Modifier.height(18.dp))

                // ── Custos ─────────────────────────────────────────────────
                SectionTitle("Custos")
                SectionCard {
                    NumberField(financiamento, { financiamento = it }, "Financiamento Mensal (R$)")
                    Spacer(Modifier.height(8.dp))
                    NumberField(manutencao, { manutencao = it }, "Manutenção Mensal (R$)")
                    Spacer(Modifier.height(8.dp))
                    NumberField(seguro, { seguro = it }, "Seguro Mensal (R$)")
                    Spacer(Modifier.height(8.dp))
                    NumberField(outros, { outros = it }, "Outros Custos Mensais (R$)")
                    Spacer(Modifier.height(8.dp))
                    NumberField(impostos, { impostos = it }, "Impostos e Taxas Anuais (R$)")
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CARD_INNER).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Custo Fixo Mensal", fontSize = 12.sp, color = TEXT_MUTED, modifier = Modifier.weight(1f))
                        Text(custoFixoMes.brl(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF40C4FF))
                    }
                }

                Spacer(Modifier.height(18.dp))

                // ── Combustível ────────────────────────────────────────────
                SectionTitle("Combustível")
                SectionCard {
                    Text("Tipo de Combustível", fontSize = 12.sp, color = TEXT_MUTED)
                    Spacer(Modifier.height(6.dp))
                    SingleChoiceChips(
                        options = listOf("Gasolina", "Etanol", "GNV", "Elétrico"),
                        selected = fuelLabel(fuelType),
                        onSelect = { label ->
                            val novo = labelToFuel(label)
                            if (novo == FuelType.ELECTRIC && fuelType != FuelType.ELECTRIC) {
                                fuelPrice = "1.20"; maintKm = "0.08"
                            } else if (novo != FuelType.ELECTRIC && fuelType == FuelType.ELECTRIC) {
                                fuelPrice = "5.85"; maintKm = "0.20"
                            }
                            fuelType = novo
                        }
                    )
                    Spacer(Modifier.height(12.dp))

                    if (isElectric) {
                        NumberField(autonomia, { autonomia = it }, "Autonomia por Carga", "Km")
                        Spacer(Modifier.height(8.dp))
                        NumberField(fuelPrice, { fuelPrice = it }, "Preço por kWh", "/kWh")
                        Spacer(Modifier.height(8.dp))
                        NumberField(capacidade, { capacidade = it }, "Capacidade da Bateria", "kWh")
                        Spacer(Modifier.height(8.dp))
                        val custoCarga = capacidade.toDoubleSafe(0.0) * fuelPrice.toDoubleSafe(0.0)
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CARD_INNER).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Custo Estimado por Carga", fontSize = 12.sp, color = TEXT_MUTED, modifier = Modifier.weight(1f))
                            Text(custoCarga.brl(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ACCENT)
                        }
                    } else {
                        NumberField(fuelPrice, { fuelPrice = it }, "Preço do Combustível (R$/${fuelType.unitLabel})")
                        Spacer(Modifier.height(8.dp))
                        NumberField(consumption, { consumption = it }, "Consumo (km/${fuelType.unitLabel})")
                    }
                    Spacer(Modifier.height(8.dp))
                    NumberField(maintKm, { maintKm = it }, "Manutenção por km (R$)")
                    Spacer(Modifier.height(8.dp))
                    NumberField(deprecKm, { deprecKm = it }, "Depreciação por km (R$)")
                }

                Spacer(Modifier.height(90.dp))
            }
        }

        // ── Barra inferior ─────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().background(SURF).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Custo por Km", fontSize = 11.sp, color = TEXT_MUTED)
                Text(previewKm.brl() + "/km", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
            Button(
                onClick = { salvar() },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ACCENT),
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp)
            ) {
                Text("Salvar", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun RowScope.VehicleTypeOption(label: String, selected: String, onSelect: (String) -> Unit) {
    val sel = label == selected
    val icon = when (label) {
        "Carro" -> Icons.Default.DirectionsCar
        "Moto" -> Icons.Default.TwoWheeler
        else -> Icons.Default.DirectionsBike
    }
    Card(
        modifier = Modifier.weight(1f).clickable { onSelect(label) },
        colors = CardDefaults.cardColors(containerColor = if (sel) Color(0xFF12261C) else CARD_INNER),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (sel) ACCENT else BORDER),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = if (sel) ACCENT else TEXT_MUTED, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(6.dp))
            Text(label, fontSize = 12.sp, color = if (sel) ACCENT else Color.White)
        }
    }
}

@Composable
private fun SituationOption(label: String, selected: String, modifier: Modifier, onSelect: (String) -> Unit) {
    val sel = label == selected
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (sel) Color(0xFF12261C) else CARD_INNER)
            .background(if (sel) Color(0xFF12261C) else CARD_INNER)
            .clickable { onSelect(label) }
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
            color = if (sel) ACCENT else Color.White
        )
    }
}

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
