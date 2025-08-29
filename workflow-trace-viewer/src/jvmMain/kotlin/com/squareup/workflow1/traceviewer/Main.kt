package com.squareup.workflow1.traceviewer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.name
import kotlin.system.exitProcess

/**
 * Main entry point for the desktop application, see [README.md] for more details.
 */
fun main() {
  application {
    var openWindows by remember { mutableStateOf(setOf<TraceWindow>()) }
    var isMainWindowOpen by remember { mutableStateOf(true) }

    // Main window - always visible
    if (isMainWindowOpen) {
      Window(
        onCloseRequest = {
          if (openWindows.isEmpty()) {
            exitProcess(0)
          }
          isMainWindowOpen = false
        },
        title = "Workflow Trace Viewer",
        state = rememberWindowState()
      ) {
        MainApp(
          modifier = Modifier.fillMaxSize(),
          onFileSelected = { file ->
            openWindows = openWindows + TraceWindow.FileWindow(file)
          },
          onDeviceSelected = { device ->
            openWindows = openWindows + TraceWindow.DeviceWindow(device)
          }
        )
      }
    }
    
    // Additional windows for each opened trace
    for (window in openWindows) {
      Window(
        onCloseRequest = {
          openWindows = openWindows - window
          if (!isMainWindowOpen && openWindows.isEmpty()) {
            exitProcess(0)
          }
        },
        title = when (window) {
          is TraceWindow.FileWindow -> window.file.name
          is TraceWindow.DeviceWindow -> "Live: ${window.device}"
        },
        state = rememberWindowState()
      ) {
        when (window) {
          is TraceWindow.FileWindow -> {
            App(
              modifier = Modifier.fillMaxSize(),
              initialFile = window.file,
            )
          }
          is TraceWindow.DeviceWindow -> {
            LiveApp(
              modifier = Modifier.fillMaxSize(),
              device = window.device,
              onFileSelected = { file ->
                if (file != null) {
                  openWindows = openWindows + TraceWindow.FileWindow(file)
                }
              }
            )
          }
        }
      }
    }
  }
}

sealed interface TraceWindow {
  data class FileWindow(val file: PlatformFile) : TraceWindow
  data class DeviceWindow(val device: String) : TraceWindow
}
