package com.squareup.workflow1.traceviewer.utils

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.squareup.workflow1.traceviewer.model.WorkflowNode
import com.squareup.workflow1.traceviewer.ui.DrawWorkflowTree
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.readString
import java.io.IOException

/**
 * Parses the data from the given file and initiates the workflow tree
 */
@Composable
public fun LoadWorkflowContent(
  file: PlatformFile?,
  onNodeSelect: (WorkflowNode) -> Unit,
  modifier: Modifier = Modifier
) {
  val jsonString = remember { mutableStateOf<String?>(null) }
  LaunchedEffect(file) {
    jsonString.value = file?.readString()
  }
  val root = jsonString.value?.let { fetchRoot(it) }

  if (root != null) {
    DrawWorkflowTree(root, onNodeSelect)
  } else {
    Text("Empty data or failed to parse data") // TODO: proper handling of error
  }
}

/**
 * Parses a JSON string into [WorkflowNode] with Moshi adapters
 *
 * All the caught exceptions should be handled by the caller, and appropriate UI feedback should be
 * provided to user
 */
public fun fetchRoot(
  json: String
): WorkflowNode? {
  return try {
    val moshi = Moshi.Builder()
      .add(KotlinJsonAdapterFactory())
      .build()
    val workflowAdapter = moshi.adapter(WorkflowNode::class.java)
    val root = workflowAdapter.fromJson(json)
    root
  } catch (e: JsonDataException) {
    throw JsonDataException("Failed to parse JSON: ${e.message}", e)
  } catch (e: IOException) {
    throw IOException("Malformed JSON: ${e.message}", e)
  }
}
