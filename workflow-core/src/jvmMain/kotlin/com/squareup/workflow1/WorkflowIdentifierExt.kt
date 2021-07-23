package com.squareup.workflow1

import com.squareup.workflow1.WorkflowIdentifier.Companion
import okio.Buffer
import okio.ByteString
import okio.EOFException
import org.jetbrains.annotations.TestOnly
import kotlin.reflect.KClass

/**
 * Reads a [WorkflowIdentifier] from a [ByteString] as written by [toByteStringOrNull].
 *
 * @throws IllegalArgumentException if the source does not contain a valid [WorkflowIdentifier]
 * @throws ClassNotFoundException if one of the workflow types can't be found in the class
 * loader
 */
@OptIn(ExperimentalWorkflowApi::class)
public fun WorkflowIdentifier.Companion.parse(bytes: ByteString): WorkflowIdentifier? = Buffer().let { source ->
  source.write(bytes)

  try {
    val typeString = source.readUtf8WithLength()
    val proxiedIdentifier = when (source.readByte()) {
      NO_PROXY_IDENTIFIER_TAG -> null
      PROXY_IDENTIFIER_TAG -> parse(source.readByteString())
      else -> throw IllegalArgumentException("Invalid WorkflowIdentifier")
    }

    @Suppress("UNCHECKED_CAST")
    val type = Class.forName(typeString) as Class<out Workflow<Nothing, Any, Any>>
    return WorkflowIdentifier(type.kotlin, proxiedIdentifier)
  } catch (e: EOFException ) {
    throw IllegalArgumentException("Invalid WorkflowIdentifier")
  }
}

/**
 * The [WorkflowIdentifier] that identifies the workflow this [KClass] represents.
 *
 * This workflow must not be an [ImpostorWorkflow], or this property will throw an
 * [IllegalArgumentException]. To create an identifier from the class of an [ImpostorWorkflow], use
 * the [impostorWorkflowIdentifier] function.
 */
@OptIn(ExperimentalStdlibApi::class)
@get:TestOnly
@ExperimentalWorkflowApi
public val KClass<out Workflow<*, *, *>>.workflowIdentifier: WorkflowIdentifier
  get() {
    val workflowClass = this@workflowIdentifier
    require(!ImpostorWorkflow::class.java.isAssignableFrom(workflowClass.java)) {
      "Cannot create WorkflowIdentifier from a KClass of ImpostorWorkflow: " +
        workflowClass.qualifiedName.toString()
    }
    return WorkflowIdentifier(type = workflowClass)
  }

