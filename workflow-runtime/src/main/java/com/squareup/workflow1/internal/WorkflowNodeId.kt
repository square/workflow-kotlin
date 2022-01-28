package com.squareup.workflow1.internal

import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowIdentifier
import com.squareup.workflow1.identifier
import com.squareup.workflow1.readByteStringWithLength
import com.squareup.workflow1.readUtf8WithLength
import com.squareup.workflow1.writeByteStringWithLength
import com.squareup.workflow1.writeUtf8WithLength
import okio.Buffer
import okio.ByteString

/**
 * Value type that can be used to distinguish between different workflows of different types or
 * the same type (in that case using a [name]).
 */
internal data class WorkflowNodeId(
  internal val identifier: WorkflowIdentifier,
  internal val name: String = ""
) {
  constructor(
    workflow: Workflow<*, *, *>,
    name: String = ""
  ) : this(workflow.identifier, name)

  fun matches(
    otherWorkflow: Workflow<*, *, *>,
    otherName: String
  ): Boolean = identifier == otherWorkflow.identifier && name == otherName

  internal fun toByteStringOrNull(): ByteString? {
    // If identifier is not snapshottable, neither are we.
    val identifierBytes = identifier.toByteStringOrNull() ?: return null
    return Buffer().let { sink ->
      sink.writeByteStringWithLength(identifierBytes)
      sink.writeUtf8WithLength(name)
      sink.readByteString()
    }
  }

  internal companion object {
    fun parse(bytes: ByteString): WorkflowNodeId = Buffer().let { source ->
      source.write(bytes)

      val identifierBytes = source.readByteStringWithLength()
      val identifier = WorkflowIdentifier.parse(identifierBytes)
      val name = source.readUtf8WithLength()
      return WorkflowNodeId(identifier, name)
    }
  }
}

internal fun <W : Workflow<I, O, R>, I, O, R>
W.id(key: String = ""): WorkflowNodeId = WorkflowNodeId(this, key)
