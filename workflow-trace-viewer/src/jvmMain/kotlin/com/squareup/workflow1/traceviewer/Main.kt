package com.squareup.workflow1.traceviewer

import androidx.compose.ui.window.singleWindowApplication

/**
 * Main entry point for application
 */
fun main() {
  singleWindowApplication(title = "Workflow Trace Viewer") {
    App()
  }
}
