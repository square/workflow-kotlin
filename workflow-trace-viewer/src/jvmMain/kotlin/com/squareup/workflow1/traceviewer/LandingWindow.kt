package com.squareup.workflow1.traceviewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.squareup.workflow1.traceviewer.ui.control.DisplayDevices
import com.squareup.workflow1.traceviewer.ui.control.UploadFile
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.delay

/**
 * Main window composable that shows both file upload and device selection options.
 */
@Composable
internal fun LandingWindow(
  modifier: Modifier = Modifier,
  onFileSelected: (PlatformFile) -> Unit,
  onDeviceSelected: (String) -> Unit
) {
  Box(modifier = modifier.fillMaxSize()) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(32.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Text(
        text = "Workflow Trace Viewer",
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 48.dp)
      )
      
      // File selection section
      UploadFile(
        resetOnFileSelect = { file ->
          file?.let { onFileSelected(it) }
        }
      )
      
      Text(
        text = "— OR —",
        fontSize = 14.sp,
        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
        modifier = Modifier.padding(bottom = 24.dp)
      )
      
      // Device selection section
      Text(
        text = "Connect to Device",
        fontSize = 18.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(bottom = 24.dp)
      )
      
      DisplayDevices(
        onDeviceSelect = onDeviceSelected,
      )
    }
  }
}
