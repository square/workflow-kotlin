package com.squareup.workflow1.traceviewer.ui.control

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Only give back the specific emulator device, i.e. "emulator-5554"
 */
private val emulatorRegex = Regex("""\bemulator-\d+\b""")
private const val ADB_DEVICE_LIST_POLLING_INTERVAL_MS = 3000L

@OptIn(ExperimentalMaterialApi::class)
@Composable
internal fun DisplayDevices(
  onDeviceSelect: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val devices by produceState(initialValue = listDevices()) {
    while (true) {
      delay(ADB_DEVICE_LIST_POLLING_INTERVAL_MS)
      value = listDevices()
    }
  }
  Box(
    modifier = modifier
      .fillMaxWidth(),
    contentAlignment = Alignment.Center
  ) {
    if (devices.isEmpty()) {
      Text(
        text = "No device available. Boot up a new device and restart the visualizer",
        modifier = Modifier.align(Alignment.Center)
      )
      return@Box
    }

    Column {
      devices.forEach { device ->
        key(device) {
          Card(
            onClick = {
              emulatorRegex.find(device)?.value?.let { emulator ->
                onDeviceSelect(emulator)
              }
            },
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color.Gray),
            modifier = Modifier.padding(4.dp).defaultMinSize(minWidth = 500.dp),
            elevation = 2.dp
          ) {
            Text(
              text = device,
              modifier = Modifier.align(Alignment.CenterHorizontally)
                .padding(horizontal = 16.dp, vertical = 8.dp)
            )
          }
        }
      }
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

