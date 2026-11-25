package com.example.ui.screens.blending

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.EditLogEntry

@Composable
fun EditLogDialog(
  logs: List<EditLogEntry>,
  onDismiss: () -> Unit
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text("Setup Edit Audit Log (${logs.size})", fontWeight = FontWeight.Bold)
        IconButton(onClick = onDismiss) {
          Icon(Icons.Default.Close, contentDescription = "Close")
        }
      }
    },
    text = {
      if (logs.isEmpty()) {
        Text("No setup modifications recorded yet.")
      } else {
        LazyColumn(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 400.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          items(logs) { entry ->
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Text(
                    text = entry.action,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium
                  )
                  Text(
                    text = entry.timestamp,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
                Text(
                  text = "Target: ${entry.targetName}",
                  fontWeight = FontWeight.SemiBold,
                  style = MaterialTheme.typography.bodySmall
                )
                Text(
                  text = entry.details,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }
        }
      }
    },
    confirmButton = {
      Button(onClick = onDismiss) {
        Text("Close")
      }
    }
  )
}
