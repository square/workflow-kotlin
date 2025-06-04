package com.squareup.workflow1.traceviewer

import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.IOException

/**
 * Parses a JSON string into [WorkflowNode] with Moshi adapters
 * All the caught exceptions should be handled by the caller, and appropriate UI feedback should be provided to user
 *
 */
public fun FetchRoot(json: String): WorkflowNode? {
  return try{
    val moshi = Moshi.Builder()
      .add(KotlinJsonAdapterFactory())
      .build()
    val workflowAdapter = moshi.adapter(WorkflowNode::class.java)
    val root = workflowAdapter.fromJson(json)
    root
  } catch (e: JsonDataException) {
    throw JsonDataException("Failed to parse JSON: ${e.message}", e)
  } catch (e: IOException){
    throw IOException("Malformed JSON: ${e.message}", e)
  }
}

