package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.TankRepository
import com.example.ui.components.AppScreen
import com.example.ui.components.AuthSession
import com.example.ui.components.PasswordDialog
import com.example.ui.screens.cru.*
import kotlinx.coroutines.delay
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CruWaitScreen(
  repository: TankRepository,
  onBack: (() -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  val lookupTables by repository.cruLookupTables.collectAsState()
  val settings by repository.settings.collectAsState()

  var selectedSubTab by remember { mutableStateOf(0) }
  val tabTitles = listOf("Estimator", "SOR & Activity", "WAIT Tools")

  // Shared values across sub-tabs
  var sharedWaitInEstimator by remember { mutableStateOf<Double?>(null) }
  var sharedWaitInSor by remember { mutableStateOf<Double?>(null) }

  // Estimator Base/New targets for tool pushes
  var estimatorBaseWhsvPush by remember { mutableStateOf<Double?>(null) }
  var estimatorNewWhsvPush by remember { mutableStateOf<Double?>(null) }
  var estimatorBaseMabpPush by remember { mutableStateOf<Double?>(null) }
  var estimatorNewMabpPush by remember { mutableStateOf<Double?>(null) }

  // SOR targets for tool pushes
  var sorWhsvPush by remember { mutableStateOf<Double?>(null) }
  var sorMabpPush by remember { mutableStateOf<Double?>(null) }

  // Setup security dialog
  var showPasswordDialog by remember { mutableStateOf(false) }
  var showSetupDialog by remember { mutableStateOf(false) }

  // Restore draft
  LaunchedEffect(Unit) {
    val draft = repository.getDraft("cru_wait")
    if (draft != null) {
      try {
        val json = JSONObject(draft.payloadJson)
        selectedSubTab = json.optInt("subTab", 0)
        if (json.has("sharedWaitEstimator")) sharedWaitInEstimator = json.optDouble("sharedWaitEstimator")
        if (json.has("sharedWaitSor")) sharedWaitInSor = json.optDouble("sharedWaitSor")
      } catch (_: Exception) {}
    }
  }

  // Auto-save draft (400ms debounce)
  LaunchedEffect(selectedSubTab, sharedWaitInEstimator, sharedWaitInSor) {
    delay(400L)
    val payload = JSONObject().apply {
      put("subTab", selectedSubTab)
      sharedWaitInEstimator?.let { put("sharedWaitEstimator", it) }
      sharedWaitInSor?.let { put("sharedWaitSor", it) }
    }.toString()
    repository.saveDraft("cru_wait", "default", payload, tabTitles.getOrElse(selectedSubTab) { "CRU WAIT" })
  }

  AppScreen(
    title = "CRU WAIT",
    onBack = onBack,
    actions = {
      IconButton(
        onClick = {
          if (AuthSession.isUnlocked()) {
            showSetupDialog = true
          } else {
            showPasswordDialog = true
          }
        },
        modifier = Modifier.testTag("cru_setup_tables_button")
      ) {
        Icon(Icons.Default.Tune, contentDescription = "CRU Tables Setup", tint = MaterialTheme.colorScheme.primary)
      }
    }
  ) {
    Column(
      modifier = modifier.fillMaxSize()
    ) {
      PrimaryTabRow(
        selectedTabIndex = selectedSubTab,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().testTag("cru_subtab_row")
      ) {
        tabTitles.forEachIndexed { index, title ->
          Tab(
            selected = selectedSubTab == index,
            onClick = { selectedSubTab = index },
            text = {
              Text(
                title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (selectedSubTab == index) FontWeight.Bold else FontWeight.Normal,
                style = MaterialTheme.typography.labelMedium
              )
            },
            modifier = Modifier.testTag("cru_subtab_$index")
          )
        }
      }

      Box(modifier = Modifier.fillMaxSize()) {
        when (selectedSubTab) {
          0 -> CruChangeEstimatorTab(
            lookupTables = lookupTables,
            currentWaitExternal = sharedWaitInEstimator,
            onSetCurrentWait = { sharedWaitInEstimator = it }
          )
          1 -> CruSorActivityTab(
            repository = repository,
            lookupTables = lookupTables,
            actualWaitExternal = sharedWaitInSor,
            onSetActualWait = { sharedWaitInSor = it }
          )
          2 -> CruToolsTab(
            repository = repository,
            onApplyWaitToEstimator = {
              sharedWaitInEstimator = it
              selectedSubTab = 0
            },
            onApplyWaitToSor = {
              sharedWaitInSor = it
              selectedSubTab = 1
            },
            onApplyWhsvToBase = {
              estimatorBaseWhsvPush = it
              selectedSubTab = 0
            },
            onApplyWhsvToNew = {
              estimatorNewWhsvPush = it
              selectedSubTab = 0
            },
            onApplyWhsvToSor = {
              sorWhsvPush = it
              selectedSubTab = 1
            },
            onApplyMabpToBase = {
              estimatorBaseMabpPush = it
              selectedSubTab = 0
            },
            onApplyMabpToNew = {
              estimatorNewMabpPush = it
              selectedSubTab = 0
            },
            onApplyMabpToSor = {
              sorMabpPush = it
              selectedSubTab = 1
            }
          )
        }
      }
    }
  }

  // Password guard for CRU setup
  if (showPasswordDialog) {
    PasswordDialog(
      title = "CRU Setup Guard",
      message = "Enter administrator password to edit Axens CRU lookup tables:",
      storedHash = settings.passwordHash,
      onDismiss = { showPasswordDialog = false },
      onSuccess = {
        AuthSession.unlock()
        showPasswordDialog = false
        showSetupDialog = true
      }
    )
  }

  // Setup dialog for CRU tables
  if (showSetupDialog) {
    CruSetupDialog(
      repository = repository,
      onDismiss = { showSetupDialog = false }
    )
  }
}
