package org.opendriver.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

// ── Paleta ───────────────────────────────────────────────────────────────────
val ACCENT = Color(0xFF00E676)
val BG = Color(0xFF0D1319)
val SURF = Color(0xFF131C26)
val CARD = Color(0xFF17222E)
val CARD_INNER = Color(0xFF1D2A38)
val BORDER = Color(0xFF2A3A4A)
val TEXT_MUTED = Color(0xFF8FA3B0)
val RED_SOFT = Color(0xFFEF5350)
val YELLOW_SOFT = Color(0xFFF9A825)

// ── Formatação ───────────────────────────────────────────────────────────────
fun Double.fmt2(): String = String.format(Locale.US, "%.2f", this)
fun Double.brl(): String = "R$ " + fmt2()
fun String.toDoubleSafe(fallback: Double): Double =
    this.replace(",", ".").trim().toDoubleOrNull() ?: fallback

// ── Componentes ──────────────────────────────────────────────────────────────

@Composable
fun ScreenHeader(title: String, onBack: (() -> Unit)? = null, trailing: @Composable (() -> Unit)? = null) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(SURF)
            .padding(horizontal = 8.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = Color.White)
                }
            } else {
                Spacer(Modifier.width(12.dp))
            }
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, modifier = Modifier.weight(1f))
            trailing?.invoke()
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(bottom = 8.dp, start = 2.dp))
}

@Composable
fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CARD),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = ACCENT,
    unfocusedBorderColor = BORDER,
    focusedLabelColor = ACCENT,
    unfocusedLabelColor = TEXT_MUTED,
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    cursorColor = ACCENT,
    unfocusedContainerColor = CARD_INNER,
    focusedContainerColor = CARD_INNER
)

@Composable
fun NumberField(value: String, onValueChange: (String) -> Unit, label: String, suffix: String? = null) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(if (suffix != null) "$label ($suffix)" else label, fontSize = 12.sp) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(10.dp),
        colors = fieldColors()
    )
}

@Composable
fun SingleChoiceChips(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { opt ->
            val sel = opt == selected
            FilterChip(
                selected = sel,
                onClick = { onSelect(opt) },
                label = { Text(opt, fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ACCENT, selectedLabelColor = Color.Black, labelColor = TEXT_MUTED
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true, selected = sel, borderColor = BORDER, selectedBorderColor = ACCENT,
                    borderWidth = 1.dp, selectedBorderWidth = 1.dp
                )
            )
        }
    }
}

@Composable
fun MultiChoiceChips(options: List<String>, selected: Set<String>, onToggle: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
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
                    selectedContainerColor = ACCENT, selectedLabelColor = Color.Black, labelColor = TEXT_MUTED
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true, selected = sel, borderColor = BORDER, selectedBorderColor = ACCENT,
                    borderWidth = 1.dp, selectedBorderWidth = 1.dp
                )
            )
        }
    }
}

@Composable
fun SegmentedOptions(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { opt ->
            val sel = opt == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (sel) ACCENT else CARD_INNER)
                    .clickable(enabled = !sel) { onSelect(opt) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    opt,
                    fontSize = 13.sp,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                    color = if (sel) Color.Black else Color.White
                )
            }
        }
    }
}

@Composable
fun ToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            if (subtitle.isNotEmpty()) Text(subtitle, fontSize = 11.sp, color = TEXT_MUTED)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White, checkedTrackColor = ACCENT,
                uncheckedThumbColor = Color.Gray, uncheckedTrackColor = Color(0xFF333333)
            )
        )
    }
}

@Composable
fun BreakdownRow(label: String, value: String, valueColor: Color = Color.White, bold: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = TEXT_MUTED, modifier = Modifier.weight(1f))
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
fun StatBox(label: String, value: String, valueColor: Color = Color.White, modifier: Modifier = Modifier) {
    Column(modifier = modifier.clip(RoundedCornerShape(12.dp)).background(CARD_INNER).padding(12.dp)) {
        Text(label, fontSize = 11.sp, color = TEXT_MUTED)
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

@Composable
fun PermissionRow(icon: ImageVector, title: String, isGranted: Boolean, onGrant: () -> Unit) {
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

/** (janela flutuante, acessibilidade) — re-checa ao voltar pra tela (ON_RESUME). */
@Composable
fun rememberPermissionsState(): Pair<Boolean, Boolean> {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var overlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var accessibility by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }

    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlay = Settings.canDrawOverlays(context)
                accessibility = isAccessibilityServiceEnabled(context)
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return overlay to accessibility
}

@Composable
fun RangeFilterRow(
    title: String,
    value: ClosedFloatingPointRange<Float>,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    format: (Float) -> String,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        Spacer(Modifier.height(2.dp))
        RangeSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = ACCENT, activeTrackColor = ACCENT, inactiveTrackColor = Color(0xFF333333)
            )
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Ruim " + format(value.start), fontSize = 11.sp, color = RED_SOFT)
            Text("Bom " + format(value.endInclusive), fontSize = 11.sp, color = ACCENT)
        }
    }
}
