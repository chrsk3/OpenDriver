package org.opendriver.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
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

@Composable
fun DetailsScreen(repo: PreferencesRepository, onBack: () -> Unit, onEdit: () -> Unit) {
    val scroll = rememberScrollState()
    val profile = remember { repo.getVehicleProfile() }
    val dias = repo.getDiasPorSemana().coerceAtLeast(1)
    val horas = repo.getHorasPorDia()
    val kmDia = repo.getKmPorDia()
    val metaSemanal = repo.getMetaSemanal()

    var period by remember { mutableStateOf("SEMANA") }
    var sim by remember { mutableFloatStateOf(0f) }

    val semanas = when (period) {
        "DIA" -> 1.0 / dias
        "MÊS" -> 4.33
        else -> 1.0
    }

    val custoFixoMes = repo.getFinanciamentoMensal() + repo.getManutencaoMensal() +
        repo.getSeguroMensal() + repo.getOutrosCustosMensais() + repo.getImpostosAnuais() / 12.0

    val kmPeriod = kmDia * dias * semanas
    val horasPeriod = horas * dias * semanas
    val ganhoPeriod = metaSemanal * semanas
    val custoVariavelPeriod = profile.variableCostPerKm * kmPeriod
    val custoFixoPeriod = custoFixoMes / 4.33 * semanas
    val custoTotal = custoVariavelPeriod + custoFixoPeriod
    val lucro = ganhoPeriod - custoTotal
    val pct = if (ganhoPeriod > 0) (lucro / ganhoPeriod) * 100 else 0.0

    val ganhoKm = if (kmPeriod > 0) ganhoPeriod / kmPeriod else 0.0
    val custoKm = profile.totalCostPerKm
    val ganhoHora = if (horasPeriod > 0) ganhoPeriod / horasPeriod else 0.0
    val custoHora = if (horasPeriod > 0) custoTotal / horasPeriod else 0.0

    val kmSemana = kmDia * dias
    val custoFinanc = repo.getFinanciamentoMensal() / 4.33
    val custoComb = profile.fuelCostPerKm * kmSemana
    val custoSeguro = repo.getSeguroMensal() / 4.33
    val custoTaxas = repo.getImpostosAnuais() / 52.0
    val custoSemanal = custoFinanc + custoComb + custoSeguro + custoTaxas

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Detalhes",
            onBack = onBack,
            trailing = {
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Editar", tint = Color.White) }
                IconButton(onClick = { }) { Icon(Icons.Default.Share, "Compartilhar", tint = Color.White) }
            }
        )

        Column(modifier = Modifier.padding(16.dp).verticalScroll(scroll)) {

            // ── Lucro do período ────────────────────────────────────────────
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Lucro " + periodName(period), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                SegmentedOptions(listOf("DIA", "SEMANA", "MÊS"), period) { period = it }
                Spacer(Modifier.height(14.dp))
                Text(
                    "R$ ${ganhoPeriod.fmt2()}  -  R$ ${custoTotal.fmt2()}  =",
                    fontSize = 14.sp, color = TEXT_MUTED
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(lucro.brl(), fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = if (lucro >= 0) ACCENT else RED_SOFT)
                    Spacer(Modifier.width(8.dp))
                    Text("(${pct.fmt2()}%)", fontSize = 14.sp, color = TEXT_MUTED, modifier = Modifier.padding(bottom = 5.dp))
                }

                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatBox("Ganho por Km", "R$ ${ganhoKm.fmt2()} /km", ACCENT, Modifier.weight(1f))
                    StatBox("Custo por Km", "R$ ${custoKm.fmt2()} /km", RED_SOFT, Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatBox("Ganho por Hora", "R$ ${ganhoHora.fmt2()} /hr", ACCENT, Modifier.weight(1f))
                    StatBox("Custo por Hora", "R$ ${custoHora.fmt2()} /hr", RED_SOFT, Modifier.weight(1f))
                }

                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Simule Ganhos Maiores", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.weight(1f))
                    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(ACCENT).padding(horizontal = 10.dp, vertical = 4.dp)) {
                        Text("+${sim.toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }
                Slider(
                    value = sim,
                    onValueChange = { sim = it },
                    valueRange = 0f..50f,
                    steps = 9,
                    colors = SliderDefaults.colors(thumbColor = ACCENT, activeTrackColor = ACCENT, inactiveTrackColor = Color(0xFF333333))
                )
                Button(
                    onClick = {
                        val fator = 1.0 + (sim / 100.0)
                        val p = repo.getDriverPreferences()
                        repo.saveDriverPreferences(
                            p.copy(
                                minNetPerKm = (ganhoKm * fator).coerceAtLeast(0.01),
                                minNetHourlyRate = (ganhoHora * fator).coerceAtLeast(0.01)
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ACCENT)
                ) {
                    Text("Aplicar \$/Km e \$/Hr no Semáforo", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(18.dp))

            // ── Custos semanais ─────────────────────────────────────────────
            SectionTitle("Custos Semanais")
            SectionCard {
                Text(custoSemanal.brl(), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Spacer(Modifier.height(10.dp))
                CostBar("Financiamento", custoFinanc, custoSemanal)
                CostBar("Combustível", custoComb, custoSemanal)
                CostBar("Seguro Veicular", custoSeguro, custoSemanal)
                CostBar("Taxas Anuais", custoTaxas, custoSemanal)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CostBar(label: String, value: Double, total: Double) {
    val frac = if (total > 0) (value / total).toFloat().coerceIn(0f, 1f) else 0f
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 13.sp, color = Color.White, modifier = Modifier.weight(1f))
            Text("R$ ${value.fmt2()}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF2A3A4A))
            ) {
                Box(Modifier.fillMaxWidth(frac).height(6.dp).clip(RoundedCornerShape(3.dp)).background(RED_SOFT))
            }
            Spacer(Modifier.width(8.dp))
            Text("${(frac * 100).toInt()}%", fontSize = 11.sp, color = TEXT_MUTED)
        }
    }
}

private fun periodName(p: String): String = when (p) {
    "DIA" -> "Diário"
    "MÊS" -> "Mensal"
    else -> "Semanal"
}
