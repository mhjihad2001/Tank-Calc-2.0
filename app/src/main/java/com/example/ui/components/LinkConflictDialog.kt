package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.LinkConflict

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkConflictResolutionDialog(
  conflicts: List<LinkConflict>,
  onResolveAll: (Map<String, String?>) -> Unit,
  onDismiss: () -> Unit
) {
  // Store chosen productId per tankId
  val selections = remember(conflicts) {
    mutableStateMapOf<String, String?>().apply {
      conflicts.forEach { c ->
        put(c.tank.id, c.defaultChoiceProductId)
      }
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Resolve Link Conflicts", fontWeight = FontWeight.Bold)
      }
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          "Multiple modules had different product links for these tanks. Tank Master is now the single source of truth. Choose the official product for each tank:",
          style = MaterialTheme.typography.bodySmall
        )

        LazyColumn(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 380.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          items(conflicts, key = { it.tank.id }) { conflict ->
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                  "Tank: ${conflict.tank.name} (${conflict.tank.type.name})",
                  fontWeight = FontWeight.Bold,
                  style = MaterialTheme.typography.titleSmall,
                  color = MaterialTheme.colorScheme.primary
                )

                conflict.candidates.forEach { cand ->
                  val isSelected = selections[conflict.tank.id] == cand.productId
                  Row(
                    modifier = Modifier
                      .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    RadioButton(
                      selected = isSelected,
                      onClick = { selections[conflict.tank.id] = cand.productId }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                      Text("${cand.productName} (${cand.shortCode})", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                      Text("Source: ${cand.source}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                  }
                }
              }
            }
          }
        }
      }
    },
    confirmButton = {
      Button(onClick = {
        onResolveAll(selections.toMap())
      }) {
        Text("Save & Apply to Tank Master")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    }
  )
}
