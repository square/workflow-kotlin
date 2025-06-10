package com.squareup.workflow1.traceviewer.utils

import androidx.compose.ui.Modifier
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.squareup.workflow1.traceviewer.model.WorkflowNode
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.readString
import java.io.IOException

/**
 * Parses the data from the given file and initiates the workflow tree
 */
public suspend fun fetchTrace(
  file: PlatformFile?,
  modifier: Modifier = Modifier
): List<WorkflowNode> {
  val jsonString = file?.readString()
  return jsonString?.let { parseTrace(it) } ?: emptyList()
}

/**
 * Parses a JSON string into [WorkflowNode] with Moshi adapters
 *
 * All the caught exceptions should be handled by the caller, and appropriate UI feedback should be
 * provided to user
 */
public fun parseTrace(
  json: String
): List<WorkflowNode> {
  return try {
    val moshi = Moshi.Builder()
      .add(KotlinJsonAdapterFactory())
      .build()

    val workflowList = Types.newParameterizedType(List::class.java, WorkflowNode::class.java)
    val workflowAdapter: JsonAdapter<List<WorkflowNode>> = moshi.adapter(workflowList)
    val root = workflowAdapter.fromJson(json) ?: emptyList()
    root
  } catch (e: JsonDataException) {
    throw JsonDataException("Failed to parse JSON: ${e.message}", e)
  } catch (e: IOException) {
    throw IOException("Malformed JSON: ${e.message}", e)
  }
}
