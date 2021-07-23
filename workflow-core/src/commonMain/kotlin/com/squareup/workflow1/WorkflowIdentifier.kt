@file:JvmMultifileClass
@file:JvmName("Workflows")

package com.squareup.workflow1

import okio.ByteString
import kotlin.jvm.JvmMultifileClass
import kotlin.jvm.JvmName
import kotlin.reflect.KClass
import kotlin.reflect.KType

/**
 * Represents a [Workflow]'s "identity" and is used by the runtime to determine whether a workflow
 * is the same as one that was rendered in a previous render pass, in which case its state
 * should be re-used; or if it's a new workflow and needs to be started.
 *
 * A workflow's identity consists primarily of its concrete type (i.e. the class that implements
 * the [Workflow] interface). Two workflows of the same concrete type are considered identical.
 * However, if a workflow class implements [ImpostorWorkflow], the identifier will also include
 * that workflow's [ImpostorWorkflow.realIdentifier].
 *
 * Instances of this class are [equatable][equals] and [hashable][hashCode].
 *
 * ## Identifiers and snapshots
 *
 * Since workflows can be [serialized][StatefulWorkflow.snapshotState], workflows' identifiers must
 * also be serializable in order to match workflows back up with their snapshots when restoring.
 * However, some [WorkflowIdentifier]s may represent workflows that cannot be snapshotted. When an
 * identifier is not snapshottable, [toByteStringOrNull] will return null, and any identifiers that
 * reference [ImpostorWorkflow]s whose [ImpostorWorkflow.realIdentifier] is not snapshottable will
 * also not be snapshottable. Such identifiers are created with [unsnapshottableIdentifier], but
 * should not be used to wrap arbitrary workflows since those workflows may expect to be
 * snapshotted.
 *
 * @constructor
 * @param type The [KClass] of the [Workflow] this identifier identifies, or the [KType] of an
 * [unsnapshottableIdentifier].
 * @param proxiedIdentifier An optional identifier from [ImpostorWorkflow.realIdentifier] that will
 * be used to further narrow the scope of this identifier.
 * @param description Implementation of [describeRealIdentifier].
 */
@ExperimentalWorkflowApi
public expect class WorkflowIdentifier {
  companion object {
    fun parse(bytes: ByteString): WorkflowIdentifier?
  }
  public fun toByteStringOrNull(): ByteString?
}

@ExperimentalWorkflowApi
public expect val Workflow<*, *, *>.identifier: WorkflowIdentifier

/**
 * Creates a [WorkflowIdentifier] that is not capable of being snapshotted and will cause any
 * [ImpostorWorkflow] workflow identified by it to also not be snapshotted.
 *
 * **This function should not be used for [ImpostorWorkflow]s that wrap arbitrary workflows**, since
 * those workflows may expect to be on snapshotted. Using such identifiers _anywhere in the
 * [ImpostorWorkflow.realIdentifier] chain_ will disable snapshotting for that workflow. **This
 * function should only be used for [ImpostorWorkflow]s that wrap a closed set of known workflow
 * types.**
 */
@ExperimentalWorkflowApi
public expect fun unsnapshottableIdentifier(type: KType): WorkflowIdentifier
