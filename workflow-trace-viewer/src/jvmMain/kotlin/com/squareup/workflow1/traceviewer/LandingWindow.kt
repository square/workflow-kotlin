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
  val devices by produceState(initialValue = listDevices()) {
    while (true) {
      delay(3000L)
      value = listDevices()
    }
  }
  
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
        devices = devices
      )
    }
  }
}

/**
 * Allows users to select from multiple devices that are currently running.
 */
private fun listDevices(): List<String> {
  if (adb == null) return emptyList()
  val process = ProcessBuilder(adb, "devices", "-l").start()
  process.waitFor()
  // We drop the header "List of devices attached"
  val devices = process.inputStream.use {
    it.bufferedReader().readLines().drop(1).dropLast(1)
  }

  return devices.mapNotNull { device ->
    if (device.isBlank()) return@mapNotNull null
      val deviceId = device.split(' ').first()
      val deviceName = ProcessBuilder(adb, "-s", deviceId, "emu", "avd", "name").start()
      deviceName.waitFor()
      "$deviceId " + deviceName.inputStream.use {
        it.bufferedReader().readLines().firstOrNull() ?: ""
      }
  }
}

val adb: String? by lazy {
  listOfNotNull(
    System.getenv("ANDROID_HOME")?.let { "$it/platform-tools/adb"},
    // Common macOS Android SDK locations
    "${System.getProperty("user.home")}/Library/Android/sdk/platform-tools/adb",
    "/Users/${System.getProperty("user.name")}/Library/Android/sdk/platform-tools/adb",
  ).firstOrNull { path ->
    try {
      val process = ProcessBuilder(path, "version").start()
      if (process.waitFor() == 0) {
        return@firstOrNull true
      }
    } catch (e: Exception) {
      println(e)
    }
    return@firstOrNull false
  }
}
