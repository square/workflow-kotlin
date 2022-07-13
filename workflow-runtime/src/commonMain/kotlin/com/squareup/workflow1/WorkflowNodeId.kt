package com.squareup.workflow1

import okio.Buffer
import okio.ByteString

/**
 * Value type that can be used to distinguish between different workflows of different types or
 * the same type (in that case using a [name]).
 */
public data class WorkflowNodeId(
  internal val identifier: WorkflowIdentifier,
  internal val name: String = ""
) {
  public constructor(
    workflow: Workflow<*, *, *>,
    name: String = ""
  ) : this(workflow.identifier, name)

  internal fun matches(
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

public fun <W : Workflow<I, O, R>, I, O, R>
W.id(key: String = ""): WorkflowNodeId = WorkflowNodeId(this, key)
