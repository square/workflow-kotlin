package com.squareup.workflow1.traceviewer.util

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.squareup.workflow1.traceviewer.model.Node
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.readString

/**
 * Parses a given file's JSON String into [Node] with Moshi adapters.
 *
 * @return A [ParseResult] representing result of parsing, either an error related to the
 * format of the JSON, or a success and a parsed trace.
 */
public suspend fun parseTrace(
  file: PlatformFile,
): ParseResult {
  return try {
    val jsonString = file.readString()
    val moshi = Moshi.Builder()
      .add(KotlinJsonAdapterFactory())
      .build()
    val workflowList = Types.newParameterizedType(List::class.java, Node::class.java)
    val workflowAdapter: JsonAdapter<List<Node>> = moshi.adapter(workflowList)
    val trace = workflowAdapter.fromJson(jsonString)
    ParseResult.Success(trace)
  } catch (e: Exception) {
    ParseResult.Failure(e)
  }
}

sealed interface ParseResult {
  class Success(val trace: List<Node>?) : ParseResult
  class Failure(val error: Throwable) : ParseResult
}
