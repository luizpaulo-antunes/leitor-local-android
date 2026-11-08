package com.leitorlocal.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.leitorlocal.model.VoiceOption
import com.leitorlocal.model.AudioPreparationState
import java.util.Locale

@Composable
internal fun ReaderSettingsDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    rate: Float,
    onRateChanged: (Float) -> Unit,
    voices: List<VoiceOption>,
    selectedName: String?,
    onVoiceSelected: (String?) -> Unit,
    audioPreparation: AudioPreparationState,
    canPrepareBook: Boolean,
    onPrepareBook: () -> Unit,
    onCancelBookPreparation: () -> Unit,
) {
    if (!visible) return

    var pendingRate by remember(rate) { mutableFloatStateOf(rate) }
    val selected = voices.firstOrNull { it.name == selectedName }
    val rateLabel = String.format(Locale.forLanguageTag("pt-BR"), "%.1fx", pendingRate)
    val voiceLabel = selected?.label
        ?.substringBefore("—")
        ?.substringBefore("·")
        ?.trim()
        ?.ifBlank { null }
        ?: "Escolher voz"
    val connectionLabel = when {
        selected == null -> ""
        selected.requiresNetwork -> " · online"
        else -> " · offline"
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Ajustes de leitura",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "$voiceLabel · $rateLabel$connectionLabel",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TextButton(onClick = onDismiss) { Text("Fechar") }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                ReaderSettingsControls(
                    rate = pendingRate,
                    rateLabel = rateLabel,
                    onRateChanged = { pendingRate = it },
                    onRateChangeFinished = { onRateChanged(pendingRate) },
                    voices = voices,
                    selectedName = selectedName,
                    onVoiceSelected = onVoiceSelected,
                    audioPreparation = audioPreparation,
                    canPrepareBook = canPrepareBook,
                    onPrepareBook = onPrepareBook,
                    onCancelBookPreparation = onCancelBookPreparation,
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                )
            }
        }
    }
}

@Composable
private fun ReaderSettingsControls(
    rate: Float,
    rateLabel: String,
    onRateChanged: (Float) -> Unit,
    onRateChangeFinished: () -> Unit,
    voices: List<VoiceOption>,
    selectedName: String?,
    onVoiceSelected: (String?) -> Unit,
    audioPreparation: AudioPreparationState,
    canPrepareBook: Boolean,
    onPrepareBook: () -> Unit,
    onCancelBookPreparation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Velocidade",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = rateLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = rate,
            onValueChange = onRateChanged,
            onValueChangeFinished = onRateChangeFinished,
            valueRange = 0.6f..1.6f,
            steps = 9,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        VoiceSelector(
            voices = voices,
            selectedName = selectedName,
            onVoiceSelected = onVoiceSelected,
        )
        Text(
            text = "Miro, Dii e Kokoro funcionam totalmente offline. As demais opções vêm do Android.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 16.dp),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f),
        )
        Text(
            text = "Leitura contínua",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = audioPreparation.message.ifBlank {
                "Prepare todo o áudio do livro antes de ouvir. O processo usa a voz e a velocidade selecionadas."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 5.dp),
        )
        if (audioPreparation.isRunning || audioPreparation.isReady) {
            LinearProgressIndicator(
                progress = { audioPreparation.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
            )
            if (audioPreparation.totalChunks > 0) {
                Text(
                    text = "${audioPreparation.completedChunks} de ${audioPreparation.totalChunks} trechos",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        TextButton(
            onClick = if (audioPreparation.isRunning) {
                onCancelBookPreparation
            } else {
                onPrepareBook
            },
            enabled = audioPreparation.isRunning ||
                (canPrepareBook && !audioPreparation.isReady),
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Text(
                when {
                    audioPreparation.isRunning -> "Cancelar preparação"
                    audioPreparation.isReady -> "Livro preparado"
                    else -> "Preparar livro"
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoiceSelector(
    voices: List<VoiceOption>,
    selectedName: String?,
    onVoiceSelected: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = voices.firstOrNull { it.name == selectedName }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (voices.isNotEmpty()) expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Card(
            modifier = Modifier
                .menuAnchor(
                    type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                    enabled = voices.isNotEmpty(),
                )
                .fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Voz da narração",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        selected?.label ?: "Nenhuma voz em português encontrada",
                        maxLines = 2,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
                ExposedDropdownMenuDefaults.TrailingIcon(expanded)
            }
        }
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            voices.forEach { voice ->
                val isSelected = voice.name == selectedName
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = voice.label,
                                fontWeight = if (isSelected) {
                                    FontWeight.SemiBold
                                } else {
                                    FontWeight.Normal
                                },
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                            Text(
                                buildString {
                                    if (isSelected) append("Selecionada · ")
                                    append(if (voice.requiresNetwork) "Online" else "Offline")
                                    append(" · ${voice.localeTag}")
                                    if (voice.isHighQuality) append(" · alta qualidade")
                                    if (voice.isEmbeddedNeural) append(" · neural incorporada")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    leadingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Voz selecionada",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else {
                        null
                    },
                    onClick = {
                        onVoiceSelected(voice.name)
                        expanded = false
                    },
                )
            }
        }
    }
}
