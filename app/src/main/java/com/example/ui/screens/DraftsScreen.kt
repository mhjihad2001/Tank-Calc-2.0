package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.DraftRecord
import com.example.data.TankRepository
import com.example.ui.components.AppScreen
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DraftsScreen(
  repository: TankRepository,
  onBack: (() -> Unit)? = null,
  onOpenDraft: (moduleKey: String, contextKey: String) -> Unit
) {
  val draftsMap by repository.drafts.collectAsState()
  val draftsList = remember(draftsMap) { draftsMap.values.sortedByDescending { it.lastModified } }
  var draftToDelete by remember { mutableStateOf<DraftRecord?>(null) }
  var showClearAllConfirm by remember { mutableStateOf(false) }

  val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

  AppScreen(
    title = "Saved Drafts",
    onBack = onBack,
    actions = {
      if (draftsList.isNotEmpty()) {
        TextButton(
          onClick = { showClearAllConfirm = true },
          modifier = Modifier.testTag("clear_all_drafts_button")
        ) {
          Text("Clear All", color = MaterialTheme.colorScheme.error)
        }
      }
    }
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier.padding(12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
          Text(
            text = "Active drafts are automatically saved while typing. Drafts older than 30 days are purged automatically and excluded from backups unless checked.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      if (draftsList.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
          contentAlignment = Alignment.Center
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("No active drafts found", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.outline)
            Text(
              "Inputs in Dip, Transfer, Loss, Production Report, and other modules are saved here automatically.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.outline
            )
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          items(draftsList, key = { it.id }) { draft ->
            DraftCard(
              draft = draft,
              formattedDate = dateFormat.format(Date(draft.lastModified)),
              onOpen = { onOpenDraft(draft.moduleKey, draft.contextKey) },
              onDelete = { draftToDelete = draft }
            )
          }
        }
      }
    }
  }

  draftToDelete?.let { draft ->
    AlertDialog(
      onDismissRequest = { draftToDelete = null },
      title = { Text("Delete Draft?") },
      text = { Text("Are you sure you want to discard this draft for ${draft.moduleTitle} (${draft.contextKey.ifBlank { "default" }})?") },
      confirmButton = {
        Button(
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          onClick = {
            repository.deleteDraft(draft.id)
            draftToDelete = null
          }
        ) {
          Text("Delete", color = MaterialTheme.colorScheme.onError)
        }
      },
      dismissButton = {
        OutlinedButton(onClick = { draftToDelete = null }) {
          Text("Cancel")
        }
      }
    )
  }

  if (showClearAllConfirm) {
    AlertDialog(
      onDismissRequest = { showClearAllConfirm = false },
      title = { Text("Clear All Drafts?") },
      text = { Text("This will permanently discard all saved in-progress form drafts across all modules.") },
      confirmButton = {
        Button(
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          onClick = {
            draftsList.forEach { repository.deleteDraft(it.id) }
            showClearAllConfirm = false
          }
        ) {
          Text("Clear All", color = MaterialTheme.colorScheme.onError)
        }
      },
      dismissButton = {
        OutlinedButton(onClick = { showClearAllConfirm = false }) {
          Text("Cancel")
        }
      }
    )
  }
}

@Composable
private fun DraftCard(
  draft: DraftRecord,
  formattedDate: String,
  onOpen: () -> Unit,
  onDelete: () -> Unit
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = draft.moduleTitle,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
          )
          if (draft.contextKey.isNotBlank()) {
            Text(
              text = "Context: ${draft.contextKey}",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurface
            )
          }
        }
        Text(
          text = formattedDate,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.outline
        )
      }

      Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(
          onClick = onDelete,
          modifier = Modifier.testTag("delete_draft_${draft.moduleKey}")
        ) {
          Icon(Icons.Default.Delete, contentDescription = "Delete Draft", tint = MaterialTheme.colorScheme.error)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
          onClick = onOpen,
          modifier = Modifier.testTag("open_draft_${draft.moduleKey}")
        ) {
          Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Resume Draft")
        }
      }
    }
  }
}
