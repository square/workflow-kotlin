/*
 * Copyright 2020 Square Inc.
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
package com.squareup.workflow

import okio.BufferedSink
import okio.BufferedSource
import okio.EOFException
import kotlin.LazyThreadSafetyMode.PUBLICATION
import kotlin.reflect.KClass

/**
 * Represents a [Workflow]'s "identity" and is used by the runtime to determine whether a workflow
 * is the same as one that was rendered in a previous render pass, in which case its state
 * should be re-used; or if it's a new workflow and needs to be started.
 *
 * A workflow's identity consists primarily of its concrete type (i.e. the class that implements
 * the [Workflow] interface). Two workflows of the same concrete type are considered identical.
 *
 * However, if a workflow class implements [ImpostorWorkflow], the identifier will also include
 * that workflow's [ImpostorWorkflow.realIdentifier].
 *
 * @constructor
 * @param type The [KClass] of the [Workflow] this identifier identifies.
 * @param proxiedIdentifier An optional identifier from [ImpostorWorkflow.realIdentifier] that will
 * be used to further narrow the scope of this identifier.
 */
@ExperimentalWorkflow
class WorkflowIdentifier internal constructor(
  private val type: KClass<out Workflow<*, *, *>>,
  private val proxiedIdentifier: WorkflowIdentifier?
) {

  /**
   * The fully-qualified name of [type]. Computed lazily.
   */
  private val typeString: String by lazy(PUBLICATION) { type.java.name }

  /**
   * Returns a description of this identifier including the name of its workflow type and any
   * [proxiedIdentifier]s. Computes [typeString] if it has not already been computed.
   */
  override fun toString(): String =
    generateSequence(this) { it.proxiedIdentifier }
        .joinToString { it.typeString }
        .let { "WorkflowIdentifier($it)" }

  /**
   * Serializes this identifier to the sink. It can be read back with [WorkflowIdentifier.read].
   */
  fun write(sink: BufferedSink) {
    sink.writeUtf8WithLength(typeString)
    if (proxiedIdentifier != null) {
      sink.writeByte(PROXY_IDENTIFIER_TAG.toInt())
      proxiedIdentifier.write(sink)
    } else {
      sink.writeByte(NO_PROXY_IDENTIFIER_TAG.toInt())
    }
  }

  /**
   * Determines equality to another [WorkflowIdentifier] by comparing their [type]s and their
   * [proxiedIdentifier]s.
   */
  override fun equals(other: Any?): Boolean = when {
    this === other -> true
    other !is WorkflowIdentifier -> false
    else -> type == other.type && proxiedIdentifier == other.proxiedIdentifier
  }

  /**
   * Derives a hashcode from [type] and [proxiedIdentifier].
   */
  override fun hashCode(): Int {
    var result = type.hashCode()
    result = 31 * result + (proxiedIdentifier?.hashCode() ?: 0)
    return result
  }

  companion object {
    private const val NO_PROXY_IDENTIFIER_TAG = 0.toByte()
    private const val PROXY_IDENTIFIER_TAG = 1.toByte()

    /**
     * Reads a [WorkflowIdentifier] from [source].
     *
     * @throws IllegalArgumentException if the source does not contain a valid [WorkflowIdentifier]
     * @throws ClassNotFoundException if one of the workflow types can't be found in the class
     * loader
     */
    fun read(source: BufferedSource): WorkflowIdentifier? {
      try {
        val typeString = source.readUtf8WithLength()
        val proxiedIdentifier = when (source.readByte()) {
          NO_PROXY_IDENTIFIER_TAG -> null
          PROXY_IDENTIFIER_TAG -> read(source)
          else -> throw IllegalArgumentException("Invalid WorkflowIdentifier")
        }

        @Suppress("UNCHECKED_CAST")
        val type = Class.forName(typeString) as Class<out Workflow<Nothing, Any, Any>>
        return WorkflowIdentifier(type.kotlin, proxiedIdentifier)
      } catch (e: EOFException) {
        throw IllegalArgumentException("Invalid WorkflowIdentifier")
      }
    }
  }
}

/**
 * The [WorkflowIdentifier] that identifies this [Workflow].
 */
@ExperimentalWorkflow
val Workflow<*, *, *>.identifier: WorkflowIdentifier
  get() {
    val proxiedIdentifier = (this as? ImpostorWorkflow)?.realIdentifier
    return WorkflowIdentifier(type = this::class, proxiedIdentifier = proxiedIdentifier)
  }
