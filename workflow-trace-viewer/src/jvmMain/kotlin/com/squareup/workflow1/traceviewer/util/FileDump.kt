package com.squareup.workflow1.traceviewer.util

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults.buttonColors
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
internal fun FileDump(
  trace: String,
  modifier: Modifier
) {
  var clicked by remember { mutableStateOf(false) }
  Button (
    modifier = modifier.padding(16.dp),
    shape = CircleShape,
    colors = buttonColors(Color.Black),
    onClick = {
      clicked = true
      writeToFile(trace)
    }
  ) {
    val text = if (clicked) {
      "Trace saved to Downloads"
    } else {
      "Save trace to file"
    }
    Text(
      text = text,
      color = Color.White
    )
  }
}

private fun writeToFile(trace: String) {
  val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
  val home = System.getProperty("user.home")
  val path = "$home/Downloads/workflow-trace_$timestamp.json".toPath()

  FileSystem.SYSTEM.sink(path).use { sink ->
    sink.buffer().use { bufferedSink ->
      bufferedSink.writeUtf8("[")
      bufferedSink.writeUtf8(trace.dropLast(1)) // Fenceposting final comma
      bufferedSink.writeUtf8("]")
    }
  }
}

