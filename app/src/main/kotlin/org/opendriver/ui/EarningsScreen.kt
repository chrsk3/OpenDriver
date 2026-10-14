package org.opendriver.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.opendriver.data.PreferencesRepository

private val METRIC_IDS = listOf("Ganho/km", "Ganho/h", "Nota", "Lucro")

@Composable
fun EarningsScreen(
    repo: PreferencesRepository,
    onBack: () -> Unit,
    onDetails: () -> Unit,
    onEdit: () -> Unit
) {
    val scroll = rememberScrollState()

    var apps by remember { mutableStateOf(repo.getSelectedApps()) }
    var prefs by remember { mutableStateOf(repo.getDriverPreferences()) }
    var metrics by remember { mutableStateOf(repo.getCardMetrics()) }
    var theme by remember { mutableStateOf(repo.getCardTheme()) }
    var palette by remember { mutableStateOf(repo.getCardPalette()) }
    var position by remember { mutableStateOf(repo.getCardPosition()) }
    var fontSize by remember { mutableStateOf(repo.getCardFontSize().toString()) }
    var opacity by remember { mutableIntStateOf(repo.getFloatingButtonOpacity()) }
    var seconds by remember { mutableStateOf(repo.getCardDurationSeconds().toString()) }
    var textNotif by remember { mutableStateOf(repo.getTextNotificationEnabled()) }

    val metaSemanal = repo.getMetaSemanal()

    fun savePrefs(p: org.opendriver.core.model.DriverPreferences) {
        prefs = p
        repo.saveDriverPreferences(p)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader("Cálculo de Ganhos", onBack)

        Column(modifier = Modifier.padding(16.dp).verticalScroll(scroll)) {

            // ── Histórico ───────────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onDetails() },
                colors = CardDefaults.cardColors(containerColor = CARD),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, null, tint = ACCENT, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Histórico de Corridas", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ChevronRight, null, tint = TEXT_MUTED)
                }
            }

            Spacer(Modifier.height(18.dp))

            // ── Aplicativos ─────────────────────────────────────────────────
            SectionTitle("Selecione seus aplicativos")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Uber", "99", "InDrive").forEach { app ->
                    val sel = apps.contains(app)
                    Card(
                        modifier = Modifier.weight(1f).clickable {
                            apps = if (sel) apps - app else apps + app
                            repo.setSelectedApps(apps)
                        },
                        colors = CardDefaults.cardColors(containerColor = if (sel) Color(0xFF12261C) else CARD),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (sel) ACCENT else Color(0xFF333333)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (sel) Icon(Icons.Default.Check, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(app, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // ── Meta e filtros ──────────────────────────────────────────────
            SectionTitle("Meta e filtros de corrida")
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.TrendingUp, null, tint = ACCENT, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Meta de Lucro Semanal", fontSize = 13.sp, color = TextMuted())
                        Text("R$ ${metaSemanal.fmt2()}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    OutlinedButton(
                        onClick = onDetails,
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ACCENT),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ACCENT),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                    ) { Text("Ver", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onEdit,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ACCENT),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                    ) { Text("Editar", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold) }
                }

                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = BORDER)
                Spacer(Modifier.height(6.dp))

                RangeFilterRow(
                    title = "Ganho por Km",
                    value = prefs.ruimNetPerKm.toFloat()..prefs.minNetPerKm.toFloat(),
                    valueRange = 0f..8f,
                    steps = 15,
                    format = { "R$ ${"%.2f".format(it)}" },
                    onValueChange = { r -> savePrefs(prefs.copy(ruimNetPerKm = r.start.toDouble(), minNetPerKm = r.endInclusive.toDouble())) }
                )
                RangeFilterRow(
                    title = "Ganho por Hora",
                    value = prefs.ruimNetHourlyRate.toFloat()..prefs.minNetHourlyRate.toFloat(),
                    valueRange = 0f..120f,
                    steps = 23,
                    format = { "R$ ${"%.0f".format(it)}" },
                    onValueChange = { r -> savePrefs(prefs.copy(ruimNetHourlyRate = r.start.toDouble(), minNetHourlyRate = r.endInclusive.toDouble())) }
                )
                RangeFilterRow(
                    title = "Nota do passageiro",
                    value = prefs.ruimPassengerRating.toFloat()..prefs.minPassengerRating.toFloat(),
                    valueRange = 3f..5f,
                    steps = 19,
                    format = { "%.2f ★".format(it) },
                    onValueChange = { r -> savePrefs(prefs.copy(ruimPassengerRating = r.start.toDouble(), minPassengerRating = r.endInclusive.toDouble())) }
                )
            }

            Spacer(Modifier.height(18.dp))

            // ── Aparência do cartão ─────────────────────────────────────────
            SectionTitle("Aparência do cartão")
            CardPreview(metrics = metrics, theme = theme, palette = palette)
            Spacer(Modifier.height(10.dp))
            SectionCard {
                Text("Ordem das métricas", fontSize = 12.sp, color = TextMuted())
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    METRIC_IDS.forEach { id ->
                        val sel = metrics.contains(id)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (sel) ACCENT else CARD_INNER)
                                .clickable {
                                    metrics = if (sel) metrics - id else metrics + id
                                    repo.setCardMetrics(metrics)
                                }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(id, fontSize = 12.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, color = if (sel) Color.Black else Color.White)
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text("Tema", fontSize = 12.sp, color = TextMuted())
                Spacer(Modifier.height(6.dp))
                SegmentedOptions(listOf("Escuro", "Claro"), theme) { theme = it; repo.setCardTheme(it) }

                Spacer(Modifier.height(14.dp))
                Text("Posição", fontSize = 12.sp, color = TextMuted())
                Spacer(Modifier.height(6.dp))
                SegmentedOptions(listOf("Esquerda", "Centro", "Direita"), position) { position = it; repo.setCardPosition(it) }

                Spacer(Modifier.height(14.dp))
                Text("Paleta de cores", fontSize = 12.sp, color = TextMuted())
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("Verde", "Azul", "Roxo").forEach { p ->
                        val col = paletteColor(p)
                        val sel = palette == p
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(50))
                                .background(col)
                                .clickable { palette = p; repo.setCardPalette(p) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (sel) Icon(Icons.Default.Check, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Opacidade", fontSize = 12.sp, color = TextMuted(), modifier = Modifier.weight(1f))
                    Text("$opacity%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ACCENT)
                }
                Slider(
                    value = opacity.toFloat(),
                    onValueChange = { opacity = it.toInt(); repo.setFloatingButtonOpacity(opacity) },
                    valueRange = 0f..100f, steps = 9,
                    colors = SliderDefaults.colors(thumbColor = ACCENT, activeTrackColor = ACCENT, inactiveTrackColor = Color(0xFF333333))
                )

                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Tamanho da fonte", fontSize = 12.sp, color = TextMuted(), modifier = Modifier.weight(1f))
                    Text("$fontSize", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ACCENT)
                }
                Slider(
                    value = fontSize.toFloatOrNull() ?: 15f,
                    onValueChange = { fontSize = it.toInt().toString(); repo.setCardFontSize(it.toInt()) },
                    valueRange = 10f..24f, steps = 13,
                    colors = SliderDefaults.colors(thumbColor = ACCENT, activeTrackColor = ACCENT, inactiveTrackColor = Color(0xFF333333))
                )

                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Tempo de exibição", fontSize = 12.sp, color = TextMuted(), modifier = Modifier.weight(1f))
                    Text("$seconds segundos", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ACCENT)
                }
                Slider(
                    value = seconds.toIntOrNull()?.toFloat() ?: 8f,
                    onValueChange = { seconds = it.toInt().toString(); repo.setCardDurationSeconds(it.toInt()) },
                    valueRange = 2f..30f, steps = 27,
                    colors = SliderDefaults.colors(thumbColor = ACCENT, activeTrackColor = ACCENT, inactiveTrackColor = Color(0xFF333333))
                )
            }

            Spacer(Modifier.height(18.dp))

            // ── Avançado ────────────────────────────────────────────────────
            SectionTitle("Avançado")
            SectionCard {
                ToggleRow(
                    title = "Notificação de texto",
                    subtitle = "Envia os detalhes da corrida numa notificação",
                    checked = textNotif,
                    onCheckedChange = { textNotif = it; repo.setTextNotificationEnabled(it) }
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun TextMuted(): Color = TEXT_MUTED

@Composable
private fun CardPreview(metrics: List<String>, theme: String, palette: String) {
    val dark = theme == "Escuro"
    val bg = if (dark) Color(0xFF1B1B1B) else Color.White
    val text = if (dark) Color.White else Color(0xFF111111)
    val label = if (dark) Color(0xFF9E9E9E) else Color(0xFF9E9E9E)
    val accent = paletteColor(palette)
    val values = mapOf(
        "Ganho/km" to "2,80", "Ganho/h" to "79,69", "Nota" to "4,95", "Lucro" to "17,40"
    )
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(bg)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                metrics.forEach { id ->
                    Column {
                        Text(id, fontSize = 9.sp, color = label)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(width = 4.dp, height = 22.dp).clip(RoundedCornerShape(2.dp)).background(accent))
                            Spacer(Modifier.width(5.dp))
                            Text(values[id] ?: "--", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = text)
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFF1A1A1A)).padding(horizontal = 5.dp, vertical = 2.dp)) {
                    Text("UBER", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Spacer(Modifier.width(6.dp))
                Text("0h32m · 15,2km", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = text)
            }
        }
    }
}

fun paletteColor(name: String): Color = when (name) {
    "Azul" -> Color(0xFF40C4FF)
    "Roxo" -> Color(0xFFB388FF)
    else -> ACCENT
}
