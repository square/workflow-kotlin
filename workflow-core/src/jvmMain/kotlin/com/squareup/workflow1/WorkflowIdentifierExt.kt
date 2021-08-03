@file:JvmMultifileClass
@file:JvmName("Workflows")

package com.squareup.workflow1

import org.jetbrains.annotations.TestOnly
import kotlin.reflect.KClass

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

