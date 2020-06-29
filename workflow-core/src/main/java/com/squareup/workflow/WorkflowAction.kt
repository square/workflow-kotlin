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
@file:JvmMultifileClass
@file:JvmName("Workflows")

package com.squareup.workflow

import com.squareup.workflow.WorkflowAction.Companion.toString
import com.squareup.workflow.WorkflowAction.Updater

/**
 * An atomic operation that updates the state of a [Workflow], and also optionally emits an output.
 */
interface WorkflowAction<in PropsT, StateT, out OutputT> {
  @Deprecated("Use Updater")
  class Mutator<S>(var state: S)

  /**
   * The context for calls to [WorkflowAction.apply]. Allows the action to set the
   * [state], and to emit the [setOutput].
   *
   * @param state the state that the workflow should move to. Default is the current state.
   */
  class Updater<out P, S, in O>(
    val props: P,
    var state: S
  ) {
    internal var output: WorkflowOutput<@UnsafeVariance O>? = null
      private set

    /**
     * Sets the value the workflow will emit as output when this action is applied.
     * If this method is not called, there will be no output.
     */
    fun setOutput(output: O) {
      this.output = WorkflowOutput(output)
    }
  }

  /**
   * Executes the logic for this action, including any side effects, updating [state][StateT], and
   * setting the [OutputT] to emit.
   */
  @Suppress("DEPRECATION")
  fun Updater<PropsT, StateT, OutputT>.apply() {
    val mutator = Mutator(state)
    mutator.apply()
        ?.let { setOutput(it) }
    state = mutator.state
  }

  @Suppress("DEPRECATION")
  @Deprecated("Implement Updater.apply")
  fun Mutator<StateT>.apply(): OutputT? {
    throw UnsupportedOperationException()
  }

  companion object {
    /**
     * Returns a [WorkflowAction] that does nothing: no output will be emitted, and
     * the state will not change.
     *
     * Use this to, for example, ignore the output of a child workflow or worker.
     */
    @Suppress("UNCHECKED_CAST")
    fun <PropsT, StateT, OutputT> noAction(): WorkflowAction<PropsT, StateT, OutputT> =
      NO_ACTION as WorkflowAction<Any?, StateT, OutputT>

    private val NO_ACTION = action<Any, Any, Any>({ "noAction" }) { }
  }
}

/**
 * Creates a [WorkflowAction] from the [apply] lambda.
 * The returned object will include the string returned from [name] in its [toString].
 *
 * If defining actions within a [StatefulWorkflow], use the [StatefulWorkflow.workflowAction]
 * extension instead, to do this without being forced to repeat its parameter types.
 *
 * @param name A string describing the update for debugging.
 * @param apply Function that defines the workflow update.
 *
 * @see StatelessWorkflow.action
 * @see StatefulWorkflow.action
 */
inline fun <PropsT, StateT, OutputT> action(
  name: String = "",
  crossinline apply: Updater<PropsT, StateT, OutputT>.() -> Unit
) = action({ name }, apply)

/**
 * Creates a [WorkflowAction] from the [apply] lambda.
 * The returned object will include the string returned from [name] in its [toString].
 *
 * If defining actions within a [StatefulWorkflow], use the [StatefulWorkflow.workflowAction]
 * extension instead, to do this without being forced to repeat its parameter types.
 *
 * @param name Function that returns a string describing the update for debugging.
 * @param apply Function that defines the workflow update.
 *
 * @see StatelessWorkflow.action
 * @see StatefulWorkflow.action
 */
inline fun <PropsT, StateT, OutputT> action(
  crossinline name: () -> String,
  crossinline apply: Updater<PropsT, StateT, OutputT>.() -> Unit
): WorkflowAction<PropsT, StateT, OutputT> = object : WorkflowAction<PropsT, StateT, OutputT> {
  override fun Updater<@UnsafeVariance PropsT, StateT, OutputT>.apply() = apply.invoke(this)

  override fun toString(): String = "WorkflowAction(${name()})@${hashCode()}"
}

/** Applies this [WorkflowAction] to [state]. */
@ExperimentalWorkflowApi
fun <PropsT, StateT, OutputT> WorkflowAction<PropsT, StateT, OutputT>.applyTo(
  props: PropsT,
  state: StateT
): Pair<StateT, WorkflowOutput<OutputT>?> {
  val updater = Updater<PropsT, StateT, OutputT>(props, state)
  updater.apply()
  return Pair(updater.state, updater.output)
}

/** Wrapper around a potentially-nullable [OutputT] value. */
class WorkflowOutput<out OutputT>(val value: OutputT) {
  override fun toString(): String = "WorkflowOutput($value)"

  override fun equals(other: Any?): Boolean = when {
    this === other -> true
    other !is WorkflowOutput<*> -> false
    else -> value == other.value
  }

  override fun hashCode(): Int = value.hashCode()
}
