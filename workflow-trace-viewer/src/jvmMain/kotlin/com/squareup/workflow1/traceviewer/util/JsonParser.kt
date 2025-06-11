package com.squareup.workflow1.traceviewer.util

import androidx.compose.ui.Modifier
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.squareup.workflow1.traceviewer.model.Node
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.readString
import java.io.IOException

/**
 * Parses the data from the given file and initiates the workflow tree
 */
public suspend fun fetchTrace(
  file: PlatformFile?,
  modifier: Modifier = Modifier
): List<Node> {
  val jsonString = file?.readString()
  return jsonString?.let { parseTrace(it) } ?: emptyList()
}

/**
 * Parses a JSON string into [Node] with Moshi adapters. Moshi automatically throws JsonDataException
 * and IOException
 * @throws JsonDataException malformed JSON data or an error reading.
 * @throws IOException JSON is correct, but mismatch between class and JSON structure.
 */
public fun parseTrace(
  json: String
): List<Node> {
  val moshi = Moshi.Builder()
    .add(KotlinJsonAdapterFactory())
    .build()

  val workflowList = Types.newParameterizedType(List::class.java, Node::class.java)
  val workflowAdapter: JsonAdapter<List<Node>> = moshi.adapter(workflowList)
  val root = workflowAdapter.fromJson(json) ?: emptyList()
  return root
}
