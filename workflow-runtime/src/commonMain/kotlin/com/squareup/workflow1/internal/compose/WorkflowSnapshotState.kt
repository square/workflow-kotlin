package com.squareup.workflow1.internal.compose

import androidx.compose.runtime.snapshots.StateObject
import androidx.compose.runtime.snapshots.StateRecord
import androidx.compose.runtime.snapshots.withCurrent
import androidx.compose.runtime.snapshots.writable
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.applyTo

/**
 * Custom snapshot [StateObject] that stores the state for a workflow session (props, state, and
 * output handler).
 *
 * None of the accessors notify read observers—it is expected that the recompose scope responsible
 * for the render pass will be explicitly invalidated when [applyAction] updates the state.
 */
internal class WorkflowSnapshotState(
  props: Any?,
  onOutput: ((Any?) -> Unit)?,
  state: Any?,
) : StateObject {
  private var record = Record(props, onOutput, state)

  override val firstStateRecord: StateRecord
    get() = record

  override fun prependStateRecord(value: StateRecord) {
    record = value as Record
  }

  /** Returns the workflow's current state without notifying read observers. */
  fun peekState(): Any? = record.withCurrent { it.state }

  /**
   * Performs all the state update duties for a render pass:
   *
   * - Stores new props and output handlers.
   * - Calls the workflow's [onPropsChanged] method only if necessary, and updates the workflow's
   *   state if a new one is returned.
   * - Returns the latest state for the workflow to be used by the render method.
   * - Avoids calling `equals` on [newProps] and [newOnOutput] if [didPropsChange] and
   *   [didOnOutputChange] are not null.
   *
   * This should be called once every render pass.
   *
   * @param didPropsChange Optional flag that can be passed if the caller has already determined if
   * the props are different from the last call (e.g. via Compose's mechanisms). If null, the props'
   * `equals` method will be used. NB: If this is `false`, then [newProps] won't be saved even if
   * it's different than the last props.
   * @param didOnOutputChange Optional flag that can be passed if the caller has already determined
   * if the output handler is different from the last call. NB: If this is `false`, then
   * [newOnOutput] won't be saved even if it's different than the last output handler.
   * @param onPropsChanged Handler invoked when props are different. Should call
   * [com.squareup.workflow1.StatefulWorkflow.onPropsChanged].
   */
  inline fun updateAndGetState(
    newProps: Any?,
    noinline newOnOutput: ((Any?) -> Unit)?,
    didPropsChange: Boolean?,
    didOnOutputChange: Boolean?,
    onPropsChanged: (oldProps: Any?, oldState: Any?) -> Any?
  ): Any? {
    var oldProps: Any? = null
    var oldOnOutput: ((Any?) -> Unit)? = null
    var oldState: Any? = null

    // Avoid recording a read since that would make the write below a backwards write, and
    // potentially trigger and infinite recomposition loop.
    record.withCurrent {
      oldProps = it.props
      oldOnOutput = it.onOutput
      oldState = it.state
    }

    var newState = oldState
    var stateChanged = false
    val propsChanged = didPropsChange == true || (didPropsChange == null && oldProps != newProps)
    if (propsChanged) {
      newState = onPropsChanged(oldProps, oldState)
      // Only call `equals` if the state may have changed.
      stateChanged = oldState != newState
    }

    // Only perform a state write if necessary. Note there is no additional cost to updating all
    // record fields if we have to update one, so we just write them all every time.
    if (propsChanged ||
      stateChanged ||
      (didOnOutputChange == true || (didOnOutputChange == null && oldOnOutput != newOnOutput))
    ) {
      record.writable(this) {
        props = newProps
        onOutput = newOnOutput
        state = newState
      }
    }

    // No need to report a read at all, since the only way the state can change (other than this
    // function) is via applyAction, which explicitly invalidates the RecomposeScope.

    return newState
  }

  /**
   * Applies a [WorkflowAction] to the workflow's state, writing the new state and invoking the
   * output handler if necessary.
   */
  inline fun applyAction(
    action: WorkflowAction<Any?, Any?, Any?>,
    onNewState: () -> Unit
  ) {
    var oldState: Any? = null
    var lastProps: Any? = null
    var onOutput: ((Any?) -> Unit)? = null
    record.withCurrent {
      lastProps = it.props
      onOutput = it.onOutput
      oldState = it.state
    }
    val (newState, applied) = action.applyTo(lastProps, oldState)
    if (oldState != newState) {
      record.writable(this) { state = newState }
      onNewState()
    }

    // Propagate the output up the workflow tree. Propagation doesn't touch our state but still
    // should be part of the critical section: For an intermediate node, while it's propagating
    // an action from one part of its subtree, if another action comes in from a different part
    // of the subtree, the second action won't propagate until the first one is done. If we
    // moved this out of the lock, then the propagations of two actions from two subtrees could
    // be interleaved to their common ancestors.
    onOutput?.let { onOutput ->
      applied.output?.value?.let(onOutput)
    }
  }

  internal class Record(
    var props: Any?,
    var onOutput: ((Any?) -> Unit)?,
    var state: Any?,
  ) : StateRecord() {
    override fun create(): StateRecord = Record(props, onOutput, state)

    override fun assign(value: StateRecord) {
      value as Record
      props = value.props
      onOutput = value.onOutput
      state = value.state
    }
  }
}
