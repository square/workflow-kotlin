package com.squareup.workflow1.traceviewer

import androidx.compose.ui.window.singleWindowApplication

fun main() {
  // FileKit.init(appId = "com.squareup.workflow1.traceviewer")
  singleWindowApplication(title = "Workflow Trace Viewer") {
    SandboxBackground { App() }
  }
}
