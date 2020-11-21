package com.squareup.workflow1.testing

import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.WorkflowOutput

/**
 * Result of a [RenderTester.render] call that can be used to verify that a [WorkflowAction] was
 * processed and perform assertions on that action.
 *
 * @see verifyAction
 * @see verifyActionResult
 */
public interface RenderTestResult<PropsT, StateT, OutputT> {

  /**
   * Asserts that the render pass handled either a workflow/worker output or a rendering event, and
   * passes the resulting [WorkflowAction] to [block] for asserting.
   *
   * If the workflow didn't process any actions, [block] will be passed [WorkflowAction.noAction].
   *
   * This is useful if your actions are a sealed class or enum. If you need to test an anonymous
   * action, use [verifyActionResult].
   */
  public fun verifyAction(block: (WorkflowAction<PropsT, StateT, OutputT>) -> Unit)

  /**
   * Asserts that the render pass handled either a workflow/worker output or a rendering event,
   * "executes" the action with the state passed to [testRender], then invokes [block] with the
   * resulting state and output values.
   *
   * If the workflow didn't process any actions, `newState` will be the initial state and `output`
   * will be null.
   *
   * Note that by using this method, you're also testing the implementation of your action. This can
   * be useful if your actions are anonymous. If they are a sealed class or enum, use [verifyAction]
   * instead and write separate unit tests for your action implementations.
   */
  public fun verifyActionResult(block: (newState: StateT, output: WorkflowOutput<OutputT>?) -> Unit)
}
