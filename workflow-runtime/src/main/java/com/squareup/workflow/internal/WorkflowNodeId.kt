/*
 * Copyright 2019 Square Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.workflow.internal

import com.squareup.workflow.ExperimentalWorkflow
import com.squareup.workflow.Workflow
import com.squareup.workflow.WorkflowIdentifier
import com.squareup.workflow.diagnostic.WorkflowDiagnosticListener
import com.squareup.workflow.identifier
import com.squareup.workflow.parse
import com.squareup.workflow.readUtf8WithLength
import com.squareup.workflow.writeUtf8WithLength
import okio.Buffer
import okio.ByteString
import kotlin.LazyThreadSafetyMode.NONE

/**
 * Value type that can be used to distinguish between different workflows of different types or
 * the same type (in that case using a [name]).
 *
 * @param diagnosticId ID used to uniquely identify this node to [WorkflowDiagnosticListener]s for
 * the current instance of the runtime. This property is not included in equals and hashcode
 * calculations.
 */
@OptIn(ExperimentalWorkflow::class)
class WorkflowNodeId
@PublishedApi internal constructor(
  internal val identifier: WorkflowIdentifier,
  internal val name: String = "",
  @Transient internal val diagnosticId: DiagnosticId = DiagnosticId.ROOT
) {
  internal constructor(
    workflow: Workflow<*, *, *>,
    name: String = "",
    diagnosticId: DiagnosticId = DiagnosticId.ROOT
  ) : this(workflow.identifier, name, diagnosticId)

  fun matches(
    otherWorkflow: Workflow<*, *, *>,
    otherName: String
  ): Boolean = identifier == otherWorkflow.identifier && name == otherName

  /**
   * String representation of this workflow's type (i.e. [WorkflowIdentifier]), suitable for use in
   * diagnostic output (see
   * [WorkflowHierarchyDebugSnapshot][com.squareup.workflow.diagnostic.WorkflowHierarchyDebugSnapshot]
   * ).
   */
  val typeDebugString: String by lazy(NONE) { identifier.toString() }

  override fun equals(other: Any?): Boolean = when {
    this === other -> true
    other !is WorkflowNodeId -> false
    else -> identifier == other.identifier && name == other.name
  }

  override fun hashCode(): Int {
    var result = identifier.hashCode()
    result = 31 * result + name.hashCode()
    return result
  }
}

@OptIn(ExperimentalWorkflow::class)
internal fun WorkflowNodeId.toByteString(): ByteString = Buffer()
    .also { sink ->
      identifier.write(sink)
      sink.writeUtf8WithLength(name)
    }
    .readByteString()

/**
 * Read a [WorkflowNodeId] from [bytes] and returns it with the default diagnostic iD.
 */
@OptIn(ExperimentalWorkflow::class)
internal fun restoreId(bytes: ByteString): WorkflowNodeId = bytes.parse { source ->
  val identifier = WorkflowIdentifier.read(source)
      ?: throw ClassCastException("Invalid WorkflowIdentifier in ByteString")
  val name = source.readUtf8WithLength()
  return WorkflowNodeId(identifier, name = name)
}
