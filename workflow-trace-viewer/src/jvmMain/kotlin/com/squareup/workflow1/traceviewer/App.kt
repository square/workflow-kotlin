package com.squareup.workflow1.traceviewer

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.squareup.workflow1.traceviewer.FileReader.fetchFile
import io.github.vinceglb.filekit.PlatformFile

@Composable
fun App() {
  val jsonString = fetchFile()
  val root = jsonString?.let { FetchRoot(it) }

  var selectedFile = remember { mutableStateOf<PlatformFile?>(null)}

  // UploadFile { selectedFile.value = it }

  if (root != null) {
    DrawWorkflowTree(root)
  } else {
    Text("Empty data or failed to parse data") // TODO: proper handling of error
  }
}


object FileReader {
  fun fetchFile() : String?{
    return javaClass.getResource("/workflow-simple.json")?.readText()
  }
}

//
// @Composable
// private fun UploadFile(onFileSelected: (PlatformFile?) -> Unit) {
//   val launcher = rememberFilePickerLauncher(
//     type = FileKitType.Custom(listOf("json","txt")),
//     title = "Select Workflow Trace File"
//   ) {
//     onFileSelected(it)
//   }
// }
