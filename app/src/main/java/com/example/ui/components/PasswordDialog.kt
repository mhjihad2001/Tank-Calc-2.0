package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.engine.SecurityUtils

object AuthSession {
  private var lastUnlockTime: Long = 0L
  private const val TIMEOUT_MS = 10 * 60 * 1000L // 10 minutes

  fun isUnlocked(): Boolean {
    if (System.currentTimeMillis() - lastUnlockTime < TIMEOUT_MS) {
      return true
    }
    return false
  }

  fun unlock() {
    lastUnlockTime = System.currentTimeMillis()
  }

  fun lock() {
    lastUnlockTime = 0L
  }
}

@Composable
fun PasswordDialog(
  title: String = "Authentication Required",
  message: String = "Enter password to perform this protected action:",
  storedHash: String,
  onDismiss: () -> Unit,
  onSuccess: () -> Unit
) {
  var passwordInput by remember { mutableStateOf("") }
  var errorMsg by remember { mutableStateOf<String?>(null) }
  val haptics = com.example.engine.rememberHapticController()

  AlertDialog(
    onDismissRequest = onDismiss,
    icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
    title = { Text(title, fontWeight = FontWeight.Bold) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
          value = passwordInput,
          onValueChange = { passwordInput = it; errorMsg = null },
          label = { Text("Password (default: jihad)") },
          visualTransformation = PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
        if (errorMsg != null) {
          Text(errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        // Embedded compact Dual-Mode (ABC / 123) In-App Keyboard inside modal dialog window
        IndustrialKeypadDock(
          cellCoordinateLabel = "AUTH",
          columnTitle = "Password",
          valueText = passwordInput,
          isTextMode = true,
          onValueChange = {
            passwordInput = it
            errorMsg = null
          },
          onEnterMoveDown = {
            if (SecurityUtils.verifyPassword(passwordInput, storedHash)) {
              haptics.trigger(com.example.engine.HapticEvent.SUCCESS)
              AuthSession.unlock()
              onSuccess()
            } else {
              haptics.trigger(com.example.engine.HapticEvent.WARNING_ALERT)
              errorMsg = "Incorrect password. Please try again."
            }
          },
          onTabMoveRight = {},
          onClose = onDismiss
        )
      }
    },
    confirmButton = {
      Button(onClick = {
        if (SecurityUtils.verifyPassword(passwordInput, storedHash)) {
          haptics.trigger(com.example.engine.HapticEvent.SUCCESS)
          AuthSession.unlock()
          onSuccess()
        } else {
          haptics.trigger(com.example.engine.HapticEvent.WARNING_ALERT)
          errorMsg = "Incorrect password. Please try again."
        }
      }) {
        Text("Unlock & Proceed")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    }
  )
}
