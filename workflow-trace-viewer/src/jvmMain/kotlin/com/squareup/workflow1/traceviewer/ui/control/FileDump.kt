package com.squareup.workflow1.traceviewer.ui.control

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
  modifier: Modifier = Modifier
) {
  var filePath by remember { mutableStateOf("") }
  var clicked by remember { mutableStateOf(false) }
  Button(
    modifier = modifier
      .padding(16.dp)
      .widthIn(max = 300.dp),
    shape = CircleShape,
    colors = buttonColors(Color.Black),
    onClick = {
      clicked = true
      filePath = writeToFile(trace)
    }
  ) {
    val text = if (clicked) {
      "Trace saved to $filePath"
    } else {
      "Save trace to file"
    }
    Text(
      text = text,
      color = Color.White,
      maxLines = 3,
      softWrap = true
    )
  }
}

private fun writeToFile(trace: String): String{
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

  return path.toString()
}
