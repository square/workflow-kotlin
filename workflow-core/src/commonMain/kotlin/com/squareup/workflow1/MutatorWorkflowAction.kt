@file:JvmMultifileClass
@file:JvmName("Workflows")

package com.squareup.workflow1

import kotlin.jvm.JvmMultifileClass
import kotlin.jvm.JvmName

/**
 * Deprecated, legacy version of [WorkflowAction]. Kept around for migration.
 */
@Deprecated("Use WorkflowAction")
@Suppress("DEPRECATION")
public abstract class MutatorWorkflowAction<in PropsT, StateT, out OutputT> :
    WorkflowAction<PropsT, StateT, OutputT>() {

  @Deprecated("Use WorkflowAction.Updater")
  public class Mutator<S>(public var state: S)

  @Deprecated("Implement WorkflowAction.apply")
  public abstract fun Mutator<StateT>.apply(): OutputT?

  final override fun Updater.apply() {
    val mutator = Mutator(state)
    mutator.apply()
        ?.let { setOutput(it) }
    state = mutator.state
  }
}
