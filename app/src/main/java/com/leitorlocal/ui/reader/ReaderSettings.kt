package com.leitorlocal.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.leitorlocal.model.VoiceOption
import com.leitorlocal.model.AudioPreparationState
import com.leitorlocal.speech.formatPreparationDurationPrecise
import com.leitorlocal.speech.formatGenerationPace
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
    onPlayPreparedSample: () -> Unit,
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
                    onPlayPreparedSample = onPlayPreparedSample,
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
    onPlayPreparedSample: () -> Unit,
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
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = preparationExplanation(audioPreparation),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (
            audioPreparation.isRunning ||
            audioPreparation.isReady ||
            audioPreparation.preparedDurationMillis > 0L
        ) {
            PreparationNumbers(
                audioPreparation = audioPreparation,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            )
        }
        if (audioPreparation.sampleReady) {
            Button(
                onClick = onPlayPreparedSample,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .padding(top = 16.dp),
            ) {
                Text(if (audioPreparation.isReady) "Começar a ouvir" else "Ouvir agora")
            }
        }
        when {
            audioPreparation.isRunning -> {
                TextButton(
                    onClick = onCancelBookPreparation,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Text("Pausar preparação")
                }
            }
            !audioPreparation.isReady -> {
                FilledTonalButton(
                    onClick = onPrepareBook,
                    enabled = canPrepareBook,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .padding(top = if (audioPreparation.sampleReady) 4.dp else 16.dp),
                ) {
                    Text(
                        if (audioPreparation.preparedDurationMillis > 0L) {
                            "Continuar preparação"
                        } else {
                            "Preparar 1 min de amostra"
                        },
                    )
                }
            }
        }
        if (audioPreparation.sampleReady) {
            Text(
                text = "Enquanto você ouve, o app prepara os próximos trechos automaticamente. Não é preciso criar outra amostra.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun PreparationNumbers(
    audioPreparation: AudioPreparationState,
    modifier: Modifier = Modifier,
) {
    val hasTarget = audioPreparation.targetDurationMillis > 0L
    val remainingAudioMillis = (
        audioPreparation.targetDurationMillis - audioPreparation.preparedDurationMillis
    ).coerceAtLeast(0L)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.padding(16.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val stackMetrics = maxWidth < 300.dp || LocalDensity.current.fontScale > 1.25f
                if (stackMetrics) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PreparationMetric(
                            label = "Áudio pronto",
                            value = formatPreparationDurationPrecise(
                                audioPreparation.preparedDurationMillis,
                            ),
                        )
                        if (hasTarget) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f),
                            )
                            PreparationMetric(
                                label = "Reserva para começar",
                                value = formatPreparationDurationPrecise(
                                    audioPreparation.targetDurationMillis,
                                ),
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        PreparationMetric(
                            label = "Áudio pronto",
                            value = formatPreparationDurationPrecise(
                                audioPreparation.preparedDurationMillis,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        if (hasTarget) {
                            PreparationMetric(
                                label = "Reserva para começar",
                                value = formatPreparationDurationPrecise(
                                    audioPreparation.targetDurationMillis,
                                ),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            if (hasTarget) {
                LinearProgressIndicator(
                    progress = { audioPreparation.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                )
                Text(
                    text = if (remainingAudioMillis > 0L) {
                        "Faltam ${formatPreparationDurationPrecise(remainingAudioMillis)} de áudio"
                    } else {
                        "Pronto para começar"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (audioPreparation.isRunning && audioPreparation.sampleReady) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f),
                )
                PreparationMetric(
                    label = "Tempo estimado até começar",
                    value = audioPreparation.estimatedWaitMillis?.let(
                        ::formatPreparationDurationPrecise,
                    ) ?: "Calculando…",
                )
            }
            if (audioPreparation.sampleReady) {
                audioPreparation.realTimeFactor?.let { realTimeFactor ->
                    Text(
                        text = formatGenerationPace(realTimeFactor),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PreparationMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

private fun preparationExplanation(audioPreparation: AudioPreparationState): String = when {
    audioPreparation.message.startsWith("Não foi possível") -> audioPreparation.message
    audioPreparation.isReady ->
        "A reserva para esta sessão está pronta. Você pode começar a leitura contínua."
    audioPreparation.sampleReady && audioPreparation.isRunning ->
        "Você já pode ouvir. Se preferir, aguarde a reserva curta para reduzir pausas nesta sessão."
    audioPreparation.sampleReady ->
        "A amostra está pronta. Você pode ouvir agora ou ampliar a reserva antes de começar."
    audioPreparation.isRunning ->
        "Primeiro o app prepara 1 min de áudio para medir a velocidade deste aparelho."
    audioPreparation.message.isNotBlank() -> audioPreparation.message
    else ->
        "Prepare 1 min de áudio para validar a voz e medir este aparelho."
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
