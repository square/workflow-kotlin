@file:JvmMultifileClass
@file:JvmName("Workflows")

package com.squareup.workflow1

/**
 * Deprecated, legacy version of [WorkflowAction]. Kept around for migration.
 */
@Deprecated("Use WorkflowAction")
@Suppress("DEPRECATION")
abstract class MutatorWorkflowAction<in PropsT, StateT, out OutputT> :
    WorkflowAction<PropsT, StateT, OutputT>() {

  @Deprecated("Use WorkflowAction.Updater")
  class Mutator<S>(var state: S)

  @Deprecated("Implement WorkflowAction.apply")
  abstract fun Mutator<StateT>.apply(): OutputT?

  final override fun Updater.apply() {
    val mutator = Mutator(state)
    mutator.apply()
        ?.let { setOutput(it) }
    state = mutator.state
  }
}
