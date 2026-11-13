package com.squareup.workflow1.internal.compose

import androidx.compose.runtime.snapshots.StateObject
import androidx.compose.runtime.snapshots.StateRecord
import androidx.compose.runtime.snapshots.withCurrent
import androidx.compose.runtime.snapshots.writable
import com.squareup.workflow1.ActionApplied
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.applyTo

/**
 * Custom snapshot [StateObject] that stores the state for a workflow session (props and state).
 *
 * None of the accessors notify read observers—it is expected that the recompose scope responsible
 * for the render pass will be explicitly invalidated when [applyAction] updates the state.
 *
 * [updateAndGetState] is called from inside the render pass (i.e. inside the Recomposer's mutable
 * snapshot), and [applyAction] is called from the runtime's action loop, between render passes.
 * Both run on the runtime thread, so the two writes never race each other.
 */
internal class WorkflowSnapshotState(props: Any?, state: Any?) : StateObject {
  private var record = Record(props, state)

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
   * - Stores new props.
   * - Calls the workflow's [onPropsChanged] method only if necessary, and updates the workflow's
   *   state if a new one is returned.
   * - Returns the latest state for the workflow to be used by the render method.
   * - Avoids calling `equals` on [newProps] if [didPropsChange] is not null.
   *
   * This should be called once every render pass.
   *
   * @param didPropsChange Optional flag that can be passed if the caller has already determined if
   *   the props are different from the last call (e.g. via Compose's mechanisms). If null, the
   *   props' `equals` method will be used. NB: If this is `false`, then [newProps] won't be saved
   *   even if it's different than the last props.
   * @param onPropsChanged Handler invoked when props are different. Should call
   *   [com.squareup.workflow1.StatefulWorkflow.onPropsChanged].
   */
  inline fun updateAndGetState(
    newProps: Any?,
    didPropsChange: Boolean?,
    onPropsChanged: (oldProps: Any?, oldState: Any?) -> Any?,
  ): Any? {
    var oldProps: Any? = null
    var oldState: Any? = null

    // Avoid recording a read since that would make the write below a backwards write, and
    // potentially trigger and infinite recomposition loop.
    record.withCurrent {
      oldProps = it.props
      oldState = it.state
    }

    var newState = oldState
    val propsChanged = didPropsChange == true || (didPropsChange == null && oldProps != newProps)
    if (propsChanged) {
      newState = onPropsChanged(oldProps, oldState)
      // Only perform a state write if necessary. Note there is no additional cost to updating all
      // record fields if we have to update one, so we just write them all every time.
      record.writable(this) {
        props = newProps
        state = newState
      }
    }

    // No need to report a read at all, since the only way the state can change (other than this
    // function) is via applyAction, which explicitly invalidates the RecomposeScope.

    return newState
  }

  /**
   * Applies a [WorkflowAction] to the workflow's state, writing the new state and invoking
   * [onNewState] if the state changed. Returns the [ActionApplied] describing the result so the
   * caller can propagate any output to the parent.
   */
  inline fun applyAction(
    action: WorkflowAction<Any?, Any?, Any?>,
    onNewState: () -> Unit,
  ): ActionApplied<Any?> {
    var oldState: Any? = null
    var lastProps: Any? = null
    record.withCurrent {
      lastProps = it.props
      oldState = it.state
    }
    val (newState, applied) = action.applyTo(lastProps, oldState)
    if (applied.stateChanged) {
      record.writable(this) { state = newState }
      onNewState()
    }
    return applied
  }

  internal class Record(var props: Any?, var state: Any?) : StateRecord() {
    override fun create(): StateRecord = Record(props, state)

    override fun assign(value: StateRecord) {
      value as Record
      props = value.props
      state = value.state
    }
  }
}
