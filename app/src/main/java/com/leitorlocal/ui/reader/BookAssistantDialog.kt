package com.leitorlocal.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.leitorlocal.model.BookAssistantState

@Composable
internal fun BookAssistantDialog(
    state: BookAssistantState,
    onDismiss: () -> Unit,
    onSearch: (String) -> Unit,
    onSummarize: () -> Unit,
    onExplain: () -> Unit,
    onSourceSelected: (Int) -> Unit,
) {
    if (!state.isOpen) return
    var query by remember(state.isOpen) { mutableStateOf(state.query) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Entender o livro") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Tudo é processado offline. As respostas usam somente trechos do PDF.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("O que você procura?") },
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = { onSearch(query) }, enabled = query.isNotBlank() && !state.isWorking) {
                        Text("Buscar")
                    }
                    TextButton(onClick = onExplain, enabled = !state.isWorking) { Text("Explicar") }
                    TextButton(onClick = onSummarize, enabled = !state.isWorking) { Text("Resumir") }
                }
                if (state.isWorking) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.padding(6.dp))
                        Text("Analisando no aparelho…")
                    }
                }
                state.result?.let { result ->
                    Text(result.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(result.body, style = MaterialTheme.typography.bodyMedium)
                    result.sources.forEach { source ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSourceSelected(source.chunkIndex) },
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Text(
                                    "Página ${source.pageNumber}",
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(source.excerpt, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
    )
}
