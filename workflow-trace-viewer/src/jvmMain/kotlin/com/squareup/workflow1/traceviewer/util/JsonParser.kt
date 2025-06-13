package com.squareup.workflow1.traceviewer.util

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
 * Parses a given file's JSON String into [Node] with Moshi adapters. Moshi automatically throws JsonDataException
 * and IOException
 * @throws JsonDataException malformed JSON data or an error reading.
 * @throws IOException JSON is correct, but mismatch between class and JSON structure.
 */
public suspend fun parseTrace(
  file: PlatformFile,
): List<Node> {
  val jsonString = file.readString()

  val moshi = Moshi.Builder()
    .add(KotlinJsonAdapterFactory())
    .build()

  val workflowList = Types.newParameterizedType(List::class.java, Node::class.java)
  val workflowAdapter: JsonAdapter<List<Node>> = moshi.adapter(workflowList)
  val trace = workflowAdapter.fromJson(jsonString)
  if (trace == null) {
    throw JsonDataException("Could not parse JSON")
  }
  return trace
}
