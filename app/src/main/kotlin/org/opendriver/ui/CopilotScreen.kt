package org.opendriver.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.opendriver.core.model.BubbleSize
import org.opendriver.data.PreferencesRepository

@Composable
fun CopilotScreen(repo: PreferencesRepository, onBack: () -> Unit, onTestBubble: () -> Unit) {
    val context = LocalContext.current
    val scroll = rememberScrollState()

    var bubbleSize by remember { mutableStateOf(repo.getBubbleSize()) }
    var opacity by remember { mutableIntStateOf(repo.getFloatingButtonOpacity()) }
    var cardSeconds by remember { mutableStateOf(repo.getCardDurationSeconds().toString()) }

    var hasOverlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var hasAccessibility by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }

    LaunchedEffect(Unit) {
        hasOverlay = Settings.canDrawOverlays(context)
        hasAccessibility = isAccessibilityServiceEnabled(context)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader("Ajustes do Copiloto", onBack)

        Column(modifier = Modifier.padding(16.dp).verticalScroll(scroll)) {

            // ── Permissões ──────────────────────────────────────────────────
            SectionTitle("Permissões")
            SectionCard {
                PermissionRow(
                    icon = Icons.Default.Layers,
                    title = "Janela Flutuante",
                    isGranted = hasOverlay,
                    onGrant = {
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                        )
                    }
                )
                Spacer(Modifier.height(8.dp))
                PermissionRow(
                    icon = Icons.Default.Accessibility,
                    title = "Serviço de Acessibilidade",
                    isGranted = hasAccessibility,
                    onGrant = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                )
            }

            Spacer(Modifier.height(18.dp))

            // ── Botão flutuante ─────────────────────────────────────────────
            SectionTitle("Botão Flutuante")
            Text("Ajuste o tamanho e a transparência do botão flutuante.", fontSize = 12.sp, color = TEXT_MUTED)
            Spacer(Modifier.height(8.dp))
            SectionCard {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Tamanho", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        Text("Escolha entre pequeno, médio ou grande", fontSize = 11.sp, color = TEXT_MUTED)
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
                                contentColor = if (selected) Color.Black else TEXT_MUTED
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) ACCENT else BORDER),
                            contentPadding = PaddingValues(vertical = 6.dp)
                        ) {
                            Text(size.displayName, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = BORDER)
                Spacer(Modifier.height(14.dp))

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Opacidade", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.weight(1f))
                    Text("$opacity%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ACCENT)
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("0%", fontSize = 11.sp, color = TEXT_MUTED)
                    Slider(
                        value = opacity.toFloat(),
                        onValueChange = {
                            opacity = it.toInt()
                            repo.setFloatingButtonOpacity(opacity)
                        },
                        valueRange = 0f..100f,
                        steps = 9,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        colors = SliderDefaults.colors(thumbColor = ACCENT, activeTrackColor = ACCENT, inactiveTrackColor = Color(0xFF333333))
                    )
                    Text("100%", fontSize = 11.sp, color = TEXT_MUTED)
                }
            }

            Spacer(Modifier.height(18.dp))

            // ── Tempo do card ───────────────────────────────────────────────
            SectionTitle("Exibição do Card")
            SectionCard {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Timer, null, tint = ACCENT, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Tempo do Card", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        Text("Quanto tempo o resultado fica na tela", fontSize = 11.sp, color = TEXT_MUTED)
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

            Spacer(Modifier.height(18.dp))

            // ── Diagnóstico ─────────────────────────────────────────────────
            SectionTitle("Diagnóstico do Leitor")
            SectionCard {
                Text("Eventos de corrida lidos: ${repo.getReaderEventCount()}", fontSize = 11.sp, color = ACCENT)
                Spacer(Modifier.height(4.dp))
                Text(repo.getLastCaptureInfo(), fontSize = 11.sp, color = TEXT_MUTED)
                Spacer(Modifier.height(4.dp))
                Text("Captura completa em Android/media/org.opendriver/last_capture.txt", fontSize = 10.sp, color = Color(0xFF607D8B))
            }

            Spacer(Modifier.height(18.dp))

            Button(
                onClick = { onTestBubble() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = ACCENT),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.Black)
                Spacer(Modifier.width(8.dp))
                Text("Testar Bolha Agora", color = Color.Black, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(24.dp))
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
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CARD_INNER).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (isGranted) ACCENT else TEXT_MUTED, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(title, fontSize = 13.sp, color = Color.White, modifier = Modifier.weight(1f))
        if (isGranted) {
            Icon(Icons.Default.CheckCircle, null, tint = ACCENT, modifier = Modifier.size(20.dp))
        } else {
            TextButton(onClick = onGrant, colors = ButtonDefaults.textButtonColors(contentColor = ACCENT)) {
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
