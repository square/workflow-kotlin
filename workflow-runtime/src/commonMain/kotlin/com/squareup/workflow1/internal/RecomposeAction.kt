package com.squareup.workflow1.internal

import com.squareup.workflow1.WorkflowAction

/**
 * This action doesn't actually update state, but it's special-cased inside WorkflowNode to always
 * act like it updated state, to force a re-render and thus a recomposition.
 */
internal class RecomposeAction<PropsT, StateT, OutputT> : WorkflowAction<PropsT, StateT, OutputT>() {
  override fun Updater.apply() {
    // Noop
  }
}
