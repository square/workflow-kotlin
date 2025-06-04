package com.squareup.workflow1.traceviewer

import androidx.compose.material.Text
import androidx.compose.runtime.Composable

@Composable
fun App() {
  val jsonString = object {}.javaClass.getResource("/workflow-simple.json")?.readText()
  val root = jsonString?.let { FetchRoot(it) }
  println(root)
  if (root != null) {
    DrawWorkflowTree(root)
  } else {
    Text("Empty data or failed to parse data") // TODO: proper handling of error
  }
}
