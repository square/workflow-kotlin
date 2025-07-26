package com.squareup.workflow1.traceviewer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.singleWindowApplication

/**
 * Main entry point for the desktop application, see [README.md] for more details.
 */
fun main() {
  singleWindowApplication(title = "Workflow Trace Viewer", exitProcessOnExit = false) {
    App(Modifier.fillMaxSize())
  }
}
