package com.squareup.workflow1.testing.compose

import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.WorkflowExperimentalApi
import com.squareup.workflow1.WorkflowOutput
import com.squareup.workflow1.compose.ComposeWorkflow
import com.squareup.workflow1.internal.compose.ComposeWorkflowState
import com.squareup.workflow1.testing.RenderTestResult
import com.squareup.workflow1.testing.RenderTester

/**
 * Makes a [ComposeRenderTester] look like a [RenderTester].
 */
@OptIn(WorkflowExperimentalApi::class)
internal class ComposeRenderTesterWrapper<PropsT, OutputT, RenderingT>(
  workflow: ComposeWorkflow<PropsT, OutputT, RenderingT>,
  private val composeRenderTester: ComposeRenderTester<OutputT, RenderingT>
) : RenderTester<PropsT, ComposeWorkflowState, OutputT, RenderingT>() {

  override fun expectWorkflow(
    description: String,
    exactMatch: Boolean,
    matcher: (RenderChildInvocation) -> ChildWorkflowMatch
  ): RenderTester<PropsT, ComposeWorkflowState, OutputT, RenderingT> = apply {
    composeRenderTester.expectWorkflow(description, exactMatch, matcher)
  }

  override fun expectSideEffect(
    description: String,
    exactMatch: Boolean,
    matcher: (key: String) -> Boolean
  ): RenderTester<PropsT, ComposeWorkflowState, OutputT, RenderingT> {
    throw AssertionError(
      "Expected ComposeWorkflow to have side effect $description, " +
        "but ComposeWorkflows use Compose effects."
    )
  }

  override fun expectRemember(
    description: String,
    exactMatch: Boolean,
    matcher: (RememberInvocation) -> Boolean
  ): RenderTester<PropsT, ComposeWorkflowState, OutputT, RenderingT> {
    throw AssertionError(
      "Cannot validate calls to Compose's remember {} function through RenderTester."
    )
  }

  override fun requireExplicitWorkerExpectations():
    RenderTester<PropsT, ComposeWorkflowState, OutputT, RenderingT> {
    // Noop
    return this
  }

  override fun requireExplicitSideEffectExpectations():
    RenderTester<PropsT, ComposeWorkflowState, OutputT, RenderingT> {
    // Noop
    return this
  }

  override fun requireExplicitRememberExpectations():
    RenderTester<PropsT, ComposeWorkflowState, OutputT, RenderingT> {
    // Noop
    return this
  }

  override fun render(
    block: (rendering: RenderingT) -> Unit
  ): RenderTestResult<PropsT, ComposeWorkflowState, OutputT, RenderingT> {
    val result = composeRenderTester.render(block)
    return ComposeRenderTestResultWrapper(result)
  }
}

@OptIn(WorkflowExperimentalApi::class)
private class ComposeRenderTestResultWrapper<PropsT, OutputT, RenderingT>(
  private val composeResult: ComposeRenderTestResult<OutputT, RenderingT>
) : RenderTestResult<PropsT, ComposeWorkflowState, OutputT, RenderingT> {

  override fun verifyAction(
    block: (WorkflowAction<PropsT, ComposeWorkflowState, OutputT>) -> Unit
  ): RenderTestResult<PropsT, ComposeWorkflowState, OutputT, RenderingT> {
    throw AssertionError(
      "ComposeWorkflows do not generate WorkflowActions for child workflow" +
        "outputs. Use verifyActionResult instead."
    )
  }

  override fun verifyActionResult(
    block: (newState: ComposeWorkflowState, appliedResult: WorkflowOutput<OutputT>?) -> Unit
  ): RenderTestResult<PropsT, ComposeWorkflowState, OutputT, RenderingT> = apply {
    composeResult.verifyOutputs { outputs ->
      if (outputs.isEmpty()) {
        block(ComposeWorkflowState, null)
      } else if (outputs.size == 1) {
        block(ComposeWorkflowState, WorkflowOutput(outputs.single()))
      } else {
        throw AssertionError(
          "Expected emitOutput to have been called zero or one time, " +
            "but was called ${outputs.size} times."
        )
      }
    }
  }

  override fun testNextRender(): RenderTester<PropsT, ComposeWorkflowState, OutputT, RenderingT> {
    TODO("Not yet implemented")
    composeResult.testNextRender()
  }

  override fun testNextRenderWithProps(
    newProps: PropsT
  ): RenderTester<PropsT, ComposeWorkflowState, OutputT, RenderingT> {
    TODO("Not yet implemented")
    composeResult.testNextRender()
  }
}
