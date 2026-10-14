package org.opendriver.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.opendriver.core.model.BubbleSize
import org.opendriver.data.PreferencesRepository

@Composable
fun CopilotScreen(repo: PreferencesRepository, onBack: () -> Unit) {
    val scroll = rememberScrollState()
    var bubbleSize by remember { mutableStateOf(repo.getBubbleSize()) }
    var opacity by remember { mutableIntStateOf(repo.getFloatingButtonOpacity()) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader("Ajustes do Copiloto", onBack)

        Column(modifier = Modifier.padding(16.dp).verticalScroll(scroll)) {
            SectionTitle("Botão Flutuante")
            Text("Ajuste o tamanho e a transparência do botão flutuante.", fontSize = 12.sp, color = TEXT_MUTED)
            Spacer(Modifier.height(8.dp))

            SectionCard {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ZoomOutMap, null, tint = ACCENT, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Tamanho", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        Text("Escolha entre pequeno, médio ou grande", fontSize = 11.sp, color = TEXT_MUTED)
                    }
                    Text(bubbleSize.displayName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ACCENT)
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
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(vertical = 6.dp)
                        ) {
                            Text(size.displayName, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                HorizontalDivider(color = BORDER)
                Spacer(Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Opacity, null, tint = ACCENT, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Opacidade", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.weight(1f))
                    Text("$opacity%", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ACCENT)
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

            Spacer(Modifier.height(24.dp))
        }
    }
}
