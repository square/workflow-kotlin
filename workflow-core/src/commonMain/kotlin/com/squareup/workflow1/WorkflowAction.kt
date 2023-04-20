@file:JvmMultifileClass
@file:JvmName("Workflows")

package com.squareup.workflow1

import com.squareup.workflow1.WorkflowAction.Companion.toString
import com.squareup.workflow1.WorkflowOutput.Companion.NO_OUTPUT
import kotlin.jvm.JvmMultifileClass
import kotlin.jvm.JvmName

/**
 * An atomic operation that updates the state of a [Workflow], and also optionally emits an output.
 */
public abstract class WorkflowAction<in PropsT, StateT, out OutputT> {

  /**
   * The context for calls to [WorkflowAction.apply]. Allows the action to set the
   * [state], and to emit the [setOutput].
   *
   * @param state the state that the workflow should move to. Default is the current state.
   */
  public inner class Updater(
    public val props: @UnsafeVariance PropsT,
    public var state: StateT
  ) {
    internal val startingState = state
    internal var maybeOutput: WorkflowOutput<@UnsafeVariance OutputT> = NO_OUTPUT
      private set

    /**
     * Sets the value the workflow will emit as output when this action is applied.
     * If this method is not called, there will be no output.
     */
    public fun setOutput(output: @UnsafeVariance OutputT) {
      this.maybeOutput = WorkflowOutput(output)
    }
  }

  /**
   * Executes the logic for this action, including any side effects, updating [state][StateT], and
   * setting the [OutputT] to emit.
   */
  public abstract fun Updater.apply()

  public companion object {
    /**
     * Returns a [WorkflowAction] that does nothing: no output will be emitted, and
     * the state will not change.
     *
     * Use this to, for example, ignore the output of a child workflow or worker.
     */
    @Suppress("UNCHECKED_CAST")
    public fun <PropsT, StateT, OutputT> noAction(): WorkflowAction<PropsT, StateT, OutputT> =
      NO_ACTION as WorkflowAction<Any?, StateT, OutputT>

    private val NO_ACTION = object : WorkflowAction<Any?, Any?, Any?>() {
      override fun toString(): String = "WorkflowAction.noAction()"

      override fun Updater.apply() {
        // Noop
      }
    }
  }
}

/**
 * Creates a [WorkflowAction] from the [apply] lambda.
 * The returned object will include the string returned from [name] in its [toString].
 *
 * It is more common to use [StatefulWorkflow.action] or [StatelessWorkflow.action] instead
 * of this function directly, to avoid repeating its parameter types.
 *
 * @param name A string describing the update for debugging.
 * @param apply Function that defines the workflow update.
 *
 * @see StatelessWorkflow.action
 * @see StatefulWorkflow.action
 */
public fun <PropsT, StateT, OutputT> action(
  name: String = "",
  apply: WorkflowAction<PropsT, StateT, OutputT>.Updater.() -> Unit
): WorkflowAction<PropsT, StateT, OutputT> = action({ name }, apply)

/**
 * Creates a [WorkflowAction] from the [apply] lambda.
 * The returned object will include the string returned from [name] in its [toString].
 *
 * It is more common to use [StatefulWorkflow.action] or [StatelessWorkflow.action] instead
 * of this function directly, to avoid repeating its parameter types.
 *
 * @param name Function that returns a string describing the update for debugging.
 * @param apply Function that defines the workflow update.
 *
 * @see StatelessWorkflow.action
 * @see StatefulWorkflow.action
 */
public fun <PropsT, StateT, OutputT> action(
  name: () -> String,
  apply: WorkflowAction<PropsT, StateT, OutputT>.Updater.() -> Unit
): WorkflowAction<PropsT, StateT, OutputT> = object : WorkflowAction<PropsT, StateT, OutputT>() {
  override fun Updater.apply() = apply.invoke(this)

  override fun toString(): String = "WorkflowAction(${name()})@${hashCode()}"
}

/** Applies this [WorkflowAction] to [state]. */
public fun <PropsT, StateT, OutputT> WorkflowAction<PropsT, StateT, OutputT>.applyTo(
  props: PropsT,
  state: StateT
): Pair<StateT, ActionApplied<OutputT>> {
  val updater = Updater(props, state)
  updater.apply()
  return Pair(
    updater.state,
    ActionApplied(
      output = updater.maybeOutput.value,
      stateChanged = updater.state != updater.startingState,
      outputSet = updater.maybeOutput != NO_OUTPUT
    )
  )
}

/**
 * Box around a potentially nullable [OutputT]
 */
public class WorkflowOutput<out OutputT>(
  public val value: OutputT?
) {
  override fun toString(): String = "WorkflowOutput($value)"

  override fun equals(other: Any?): Boolean = when {
    this === other -> true
    other !is ActionApplied<*> -> false
    else -> value == other.output
  }

  override fun hashCode(): Int = value.hashCode()

  internal companion object {
    /**
     * No output was set by the action. This is a marker value that we can use when passing
     * [ActionApplied] as a result of an action.
     */
    internal val NO_OUTPUT: WorkflowOutput<Nothing> = WorkflowOutput(null)
  }
}

/**
 * An [ActionProcessingResult] is any possible outcome after the runtime does a loop of processing.
 *
 * Only [ActionApplied] needs the generic OutputT so we do not include it in the root
 * interface here.
 */
public sealed interface ActionProcessingResult

public object PropsUpdated : ActionProcessingResult

public object ActionsExhausted : ActionProcessingResult

/**
 * Result of applying an action.
 *
 * @param output: the potentially null [OutputT] value. If no output was set by the action at all
 *   this will be null and [outputSet] will be false.
 * @param stateChanged: whether or not the action changed the state.
 * @param outputSet: whether or not the action set any output. If it did not, this will be false
 *   to differentiate when [output] was explicitly set as null. Defaults to true.
 */
public data class ActionApplied<out OutputT>(
  public val output: OutputT?,
  public val stateChanged: Boolean = false,
  public val outputSet: Boolean = false
) : ActionProcessingResult
