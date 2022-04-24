package com.squareup.workflow1

import com.squareup.workflow1.WorkflowIdentifierType.Snapshottable
import org.jetbrains.annotations.TestOnly
import kotlin.reflect.KClass

/**
 * The [WorkflowIdentifier] that identifies the workflow this [KClass] represents.
 *
 * This workflow must not be an [ImpostorWorkflow], or this property will throw an
 * [IllegalArgumentException].
 */
@OptIn(ExperimentalStdlibApi::class)
@get:TestOnly
public val KClass<out Workflow<*, *, *>>.workflowIdentifier: WorkflowIdentifier
  get() {
    val workflowClass = this@workflowIdentifier
    require(!ImpostorWorkflow::class.java.isAssignableFrom(workflowClass.java)) {
      "Cannot create WorkflowIdentifier from a KClass of ImpostorWorkflow: " +
        workflowClass.qualifiedName.toString()
    }
    return WorkflowIdentifier(type = Snapshottable(workflowClass))
  }
