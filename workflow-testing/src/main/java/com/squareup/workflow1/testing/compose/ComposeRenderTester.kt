package com.squareup.workflow1.testing.compose

import androidx.compose.runtime.Composable
import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.WorkflowExperimentalApi
import com.squareup.workflow1.compose.ComposeWorkflow
import com.squareup.workflow1.compose.WorkflowComposable
import com.squareup.workflow1.testing.RenderTester
import com.squareup.workflow1.testing.RenderTester.ChildWorkflowMatch
import com.squareup.workflow1.testing.RenderTester.RenderChildInvocation

@WorkflowExperimentalApi
public fun <OutputT, RenderingT> testRender(
  produceRendering: @WorkflowComposable @Composable (emitOutput: (OutputT) -> Unit) -> RenderingT
): ComposeRenderTester<OutputT, RenderingT> {
  TODO()
}

/**
 * Create a [RenderTester] to unit test an individual render pass of this workflow.
 *
 * See [RenderTester] for usage documentation.
 */
@WorkflowExperimentalApi
public fun <PropsT, OutputT, RenderingT>
  ComposeWorkflow<PropsT, OutputT, RenderingT>.testRender(
  // props: PropsT,
  runtimeConfig: RuntimeConfig? = null
): ComposeRenderTester<OutputT, RenderingT> = TODO()

@WorkflowExperimentalApi
public interface ComposeRenderTester<OutputT, RenderingT> {

  /**
   * Specifies that this render pass is expected to render a particular child workflow.
   *
   * @param description String that will be used to describe this expectation in error messages.
   * The description is required since no human-readable description can be derived from the
   * predicate alone.
   *
   * @param exactMatch If true, then the test will fail if any other matching expectations are also
   * exact matches, and the expectation will only be allowed to match a single child workflow.
   * If false, the match will only be used if no other expectations return exclusive matches (in
   * which case the first match will be used), and the expectation may match multiple children.
   *
   * @param matcher A function that determines whether a given [RenderChildInvocation] matches this
   * expectation by returning a [ChildWorkflowMatch]. If the expectation matches, the function
   * must include the rendering and optional output for the child workflow.
   */
  public fun expectWorkflow(
    description: String,
    exactMatch: Boolean = true,
    matcher: (RenderChildInvocation) -> ChildWorkflowMatch
  ): ComposeRenderTester<OutputT, RenderingT>

  /**
   * Verifies that some snapshot state that was read by the composition was changed. If state was
   * changed, then [block] is called to allow you to perform your own assertions on your own state
   * objects.
   *
   * E.g.
   * ```kotlin
   * var myState by mutableStateOf(0)
   * testRender { MyComposable(myState, onChildOutput = { myState++ } }
   *   .expectWorkflow(…, output = Unit)
   *   .render { rendering ->
   *     assertEquals(expectedRendering, rendering)
   *     assertEquals(1, myState)
   *   }
   * ```
   */
  public fun render(
    block: (RenderingT) -> Unit = {}
  ): ComposeRenderTestResult<OutputT, RenderingT>
}

@WorkflowExperimentalApi
public interface ComposeRenderTestResult<OutputT, RenderingT> {

  /**
   * Verifies that some snapshot state that was read by the composition was changed. If state was
   * changed, then [block] is called to allow you to perform your own assertions on your own state
   * objects.
   *
   * E.g.
   * ```kotlin
   * var myState by mutableStateOf(0)
   * testRender { MyComposable(myState, onChildOutput = { myState++ } }
   *   .expectWorkflow(…, output = Unit)
   *   .render()
   *   .verifyStateChanged { assertEquals(1, myState) }
   * ```
   */
  // TODO do we even need this method? You can just do state assertions in the render callback.
  public fun verifyStateChanged(
    block: () -> Unit = {}
  ): ComposeRenderTestResult<OutputT, RenderingT>

  /**
   * Passes [block] a list of all the values passed to `emitOutput` from child workflows from the
   * last render pass. If `emitOutput` was never called, the list will be empty.
   *
   * To verify a single call to `emitOutput`, call [verifyOutput].
   */
  public fun verifyOutputs(
    block: (List<OutputT>) -> Unit
  ): ComposeRenderTestResult<OutputT, RenderingT>

  /**
   * Returns a [ComposeRenderTester] that allows you to perform a recomposition.
   */
  public fun testNextRender(): ComposeRenderTester<OutputT, RenderingT>
}

/**
 * Verifies that the workflow called `emitOutput` exactly once and runs [block] with the value
 * passed to `emitOutput`.
 *
 * To verify multiple calls to `emitOutput` from a single render pass, call
 * [ComposeRenderTestResult.verifyOutputs].
 *
 * E.g.
 * ```kotlin
 * testRender { emitOutput ->
 *   MyComposable(
 *     myState,
 *     onChildOutput = { if (it == "boo!") emitOutput("ahh!") }
 *   )
 * }
 *   .expectWorkflow(…, output = "boo!")
 *   .render()
 *   .verifyOutput { output -> assertEquals("ahh!", output) }
 * ```
 */
@WorkflowExperimentalApi
public fun <OutputT, RenderingT> ComposeRenderTestResult<OutputT, RenderingT>.verifyOutput(
  block: (OutputT) -> Unit
): ComposeRenderTestResult<OutputT, RenderingT> = verifyOutputs { outputs ->
  if (outputs.size != 1) {
    throw AssertionError(
      "Expected emitOutput to have been called exactly once, " +
        "but was called ${outputs.size} times."
    )
  }
  block(outputs.single())
}
