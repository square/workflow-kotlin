package com.squareup.sample.thingy

import com.squareup.workflow1.NullableInitBox
import com.squareup.workflow1.Updater
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.action

internal typealias StateTransformation = (MutableList<BackStackFrame>) -> Unit

internal class ActionQueue {

  private val lock = Any()

  private val stateTransformations = mutableListOf<StateTransformation>()
  private val outputEmissions = mutableListOf<Any?>()

  fun enqueueStateTransformation(transformation: StateTransformation) {
    synchronized(lock) {
      stateTransformations += transformation
    }
  }

  fun enqueueOutputEmission(value: Any?) {
    synchronized(lock) {
      outputEmissions += value
    }
  }

  /**
   * @param onNextEmitOutputAction Called when the returned action is applied if there are more
   * outputs to emit. This callback should send another action into the sink to consume those
   * outputs.
   */
  fun consumeToAction(onNextEmitOutputAction: () -> Unit): WorkflowAction<*, *, *> =
    action(name = { "ActionQueue.consumeToAction()" }) {
      consume(onNextEmitOutputAction)
    }

  fun consumeActionsToStack(stack: MutableList<BackStackFrame>) {
    val transformations = synchronized(lock) {
      stateTransformations.toList().also {
        stateTransformations.clear()
      }
    }
    transformations.forEach {
      it(stack)
    }
  }

  private fun Updater<Any?, BackStackState, Any?>.consume(
    onNextEmitOutputAction: () -> Unit
  ) {
    var transformations: List<StateTransformation>
    var output = NullableInitBox<Any?>()
    var hasMoreOutputs = false

    // The workflow runtime guarantees serialization of WorkflowActions, so we only need to guard
    // the actual reading of the lists in this class.
    synchronized(lock) {
      transformations = stateTransformations.toList()
      stateTransformations.clear()

      if (outputEmissions.isNotEmpty()) {
        // Can't use removeFirst on JVM, it resolves to too-new JVM method.
        output = NullableInitBox(outputEmissions.removeAt(0))
        hasMoreOutputs = outputEmissions.isNotEmpty()
      }
    }

    if (output.isInitialized) {
      setOutput(output)
    }

    state = state.transformStack(transformations)

    if (hasMoreOutputs) {
      onNextEmitOutputAction()
    }
  }
}
