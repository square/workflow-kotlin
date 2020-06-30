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
package com.squareup.workflow.testing

import com.squareup.workflow.ExperimentalWorkflowApi
import com.squareup.workflow.StatefulWorkflow
import com.squareup.workflow.Worker
import com.squareup.workflow.Workflow
import com.squareup.workflow.WorkflowAction
import com.squareup.workflow.WorkflowIdentifier
import com.squareup.workflow.WorkflowOutput
import com.squareup.workflow.workflowIdentifier
import kotlin.reflect.KClass

@Deprecated(
    "Renamed to testRender",
    ReplaceWith("testRender(props)", "com.squareup.workflow.testing.testRender")
)
@Suppress("NOTHING_TO_INLINE")
inline fun <PropsT, OutputT, RenderingT> Workflow<PropsT, OutputT, RenderingT>.renderTester(
  props: PropsT
): RenderTester<PropsT, *, OutputT, RenderingT> = testRender(props)

/**
 * Create a [RenderTester] to unit test an individual render pass of this workflow, using the
 * workflow's [initial state][StatefulWorkflow.initialState].
 *
 * See [RenderTester] for usage documentation.
 */
@Suppress("UNCHECKED_CAST")
fun <PropsT, OutputT, RenderingT> Workflow<PropsT, OutputT, RenderingT>.testRender(
  props: PropsT
): RenderTester<PropsT, *, OutputT, RenderingT> {
  val statefulWorkflow = asStatefulWorkflow() as StatefulWorkflow<PropsT, Any?, OutputT, RenderingT>
  return statefulWorkflow.testRender(
      props = props,
      initialState = statefulWorkflow.initialState(props, null)
  ) as RenderTester<PropsT, Nothing, OutputT, RenderingT>
}

@Deprecated(
    "Renamed to testRender",
    ReplaceWith(
        "testRender(props, initialState)",
        "com.squareup.workflow.testing.testRender"
    )
)
/* ktlint-disable parameter-list-wrapping */
fun <PropsT, StateT, OutputT, RenderingT>
    StatefulWorkflow<PropsT, StateT, OutputT, RenderingT>.renderTester(
  props: PropsT,
  initialState: StateT
): RenderTester<PropsT, StateT, OutputT, RenderingT> = testRender(props, initialState)
/* ktlint-enable parameter-list-wrapping */

/**
 * Create a [RenderTester] to unit test an individual render pass of this workflow.
 *
 * See [RenderTester] for usage documentation.
 */
/* ktlint-disable parameter-list-wrapping */
fun <PropsT, StateT, OutputT, RenderingT>
    StatefulWorkflow<PropsT, StateT, OutputT, RenderingT>.testRender(
  props: PropsT,
  initialState: StateT
): RenderTester<PropsT, StateT, OutputT, RenderingT> =
/* ktlint-enable parameter-list-wrapping */
  RealRenderTester(this, props, initialState)

/**
 * The props must be specified, the initial state may be specified, and then all child workflows
 * and workers that are expected to run, and any outputs from them, must be specified with
 * [expectWorkflow] and [expectWorker] calls. Then call [render] and perform any assertions on the
 * rendering. An event may also be sent to the rendering if no workflows or workers emitted an
 * output. Lastly, the [RenderTestResult] returned by `render` may be used to assert on the
 * [WorkflowAction]s processed to handle events or outputs by calling
 * [verifyAction][RenderTestResult.verifyAction] or
 * [verifyActionResult][RenderTestResult.verifyActionResult].
 *
 * - All workflows that are rendered/ran by this workflow must be specified.
 * - Workers are optionally specified. Specified workers must run. Unexpected workers on a render
 *   pass do not cause a test failure.
 * - It is an error if more than one workflow or worker specifies an output.
 * - It is a test failure if any workflows or workers that were expected were not ran.
 * - It is a test failure if the workflow tried to run any workflows that were not expected.
 * - It is a test failure if no workflow or workflow emitted an output, no rendering event was
 *   invoked, and any of the action verification methods on [RenderTestResult] is called.
 *
 * ## Examples
 *
 * ### Worker output
 *
 * The following example tests a render pass that runs one worker, `SubmitLoginWorker`, which
 * is configured to have "emitted" an output, and one workflow, `ChildWorkflow`, which expects a
 * props containing "test@foo.com" and returning a `ChildRendering` as its rendering.
 *
 * It checks that the rendering properties are expected and that the output handler for the
 * `SubmitLoginWorker` returned the `CompleteLogin` action.
 *
 * ```
 * workflow
 *   .renderTester(
 *     props = MyProps(…),
 *     initialState = MyState(…)
 *   )
 *   .expectWorker(
 *     matchesWhen = { it is SubmitLoginWorker },
 *     key = "signin",
 *     output = WorkflowOutput(LoginResponse(success = true))
 *   )
 *   .expectWorkflow(
 *     workflowType = ChildWorkflow::class,
 *     key = "child",
 *     assertProps = { assertThat(it.email).isEqualTo("test@foo.com") },
 *     rendering = ChildRendering("message")
 *   )
 *   .render { rendering ->
 *     assertThat(rendering.text).isEqualTo("foo")
 *   }
 *   .verifyAction { action ->
 *     assertThat(action).isEqualTo(Action.CompleteLogin(success = true))
 *   }
 * ```
 *
 * ### Rendering event
 *
 * This is similar to the example above, but will test an event sent to the rendering instead.
 *
 * ```
 * workflow
 *   .renderTester(
 *     props = MyProps(…),
 *     initialState = MyState(…)
 *   )
 *   .expectWorker(
 *     matchesWhen = { it is SubmitLoginWorker },
 *     key = "signin"
 *   )
 *   .expectWorkflow(
 *     workflowType = ChildWorkflow::class,
 *     key = "child",
 *     assertProps = { assertThat(it.email).isEqualTo("test@foo.com") },
 *     rendering = ChildRendering("message")
 *   )
 *   .render { rendering ->
 *     rendering.onCancelClicked()
 *     assertThat(rendering.text).isEqualTo("foo")
 *   }
 *   .verifyAction { action ->
 *     assertThat(action).isEqualTo(Action.CancelLogin)
 *   }
 * ```
 *
 * ### Verify action result
 *
 * This test verifies the action _result_ instead of the action itself. This technique is useful
 * if the [WorkflowAction] is anonymous or inline.
 *
 * ```
 * val currentState = …
 * val previousState = …
 *
 * workflow
 *   .renderTester(
 *     props = MyProps(…),
 *     initialState = currentState
 *   )
 *   .render { rendering ->
 *     rendering.onCancelClicked()
 *   }
 *   .verifyActionResult { newState, output ->
 *     // Check that the workflow navigated back correctly.
 *     assertThat(newState).isEqualTo(previousState)
 *
 *     // Check that the workflow didn't emit any output from the button click.
 *     assertThat(output).isNull()
 *   }
 * ```
 *
 * ### Too many outputs
 *
 * This is an example of what **not** to do – this test will error out because a worker is emitting
 * and output _and_ a rendering event is sent.
 *
 * ```
 * workflow
 *   .renderTester(
 *     props = MyProps(…),
 *     initialState = MyState(…)
 *   )
 *   .expectWorker(
 *     matchesWhen = { it is SubmitLoginWorker },
 *     key = "signin",
 *     output = WorkflowOutput(LoginResponse(success = true))
 *   )
 *   .expectWorkflow(
 *     workflowType = ChildWorkflow::class,
 *     key = "child",
 *     assertProps = { assertThat(it.email).isEqualTo("test@foo.com") },
 *     rendering = ChildRendering("message")
 *   )
 *   .render { rendering ->
 *     // This will throw and fail the test because the SubmitLoginWorker is also configured to emit
 *     // an output.
 *     rendering.onCancelClicked()
 * ```
 */
interface RenderTester<PropsT, StateT, OutputT, RenderingT> {

  /**
   * Specifies that this render pass is expected to render a particular child workflow.
   *
   * ## Expecting impostor workflows
   *
   * Identifiers are compared using [WorkflowIdentifier.matchesActualIdentifierForTest]. If the
   * workflow-under-test renders an [ImpostorWorkflow][com.squareup.workflow.ImpostorWorkflow],
   * the expected [identifier] must match the leaf real identifier of the `ImpostorWorkflow`.
   *
   * For example, given the `mapRendering` workflow operator that returns an `ImpostorWorkflow`
   * which wraps the mapped workflow, the following expectation would succeed:
   *
   * ```
   * val workflow = Workflow.stateless<…> {
   *   renderChild(childWorkflow.mapRendering { … })
   * }
   *
   * workflow.testRender(…)
   *   .expectWorkflow(childWorkflow::class, …)
   * ```
   *
   * @param identifier The [WorkflowIdentifier] of the expected workflow. May identify any supertype
   * of the actual rendered workflow, e.g. if the workflow type is an interface and the
   * workflow-under-test injects a fake.
   * @param rendering The rendering to return from
   * [renderChild][com.squareup.workflow.RenderContext.renderChild] when this workflow is rendered.
   * @param key The key passed to [renderChild][com.squareup.workflow.RenderContext.renderChild]
   * when rendering this workflow.
   * @param assertProps A function that performs assertions on the props passed to
   * [renderChild][com.squareup.workflow.RenderContext.renderChild].
   * @param output If non-null, [WorkflowOutput.value] will be "emitted" when this workflow is
   * rendered. The [WorkflowAction] used to handle this output can be verified using methods on
   * [RenderTestResult].
   */
  @ExperimentalWorkflowApi
  fun <ChildOutputT, ChildRenderingT> expectWorkflow(
    identifier: WorkflowIdentifier,
    rendering: ChildRenderingT,
    key: String = "",
    assertProps: (props: Any?) -> Unit = {},
    output: WorkflowOutput<ChildOutputT>? = null
  ): RenderTester<PropsT, StateT, OutputT, RenderingT>

  /**
   * Specifies that this render pass is expected to render a particular child workflow.
   *
   * ## Expecting impostor workflows
   *
   * Identifiers are compared using [WorkflowIdentifier.matchesActualIdentifierForTest]. If the
   * workflow-under-test renders an [ImpostorWorkflow][com.squareup.workflow.ImpostorWorkflow],
   * the expected [identifier] must match the leaf real identifier of the `ImpostorWorkflow`.
   *
   * For example, given the `mapRendering` workflow operator that returns an `ImpostorWorkflow`
   * which wraps the mapped workflow, the following expectation would succeed:
   *
   * ```
   * val workflow = Workflow.stateless<…> {
   *   renderChild(childWorkflow.mapRendering { … })
   * }
   *
   * workflow.testRender(…)
   *   .expectWorkflow(childWorkflow::class, …)
   * ```
   *
   * @param workflowType The [KClass] of the expected workflow. May also be any of the supertypes
   * of the expected workflow, e.g. if the workflow type is an interface and the workflow-under-test
   * injects a fake.
   * @param rendering The rendering to return from
   * [renderChild][com.squareup.workflow.RenderContext.renderChild] when this workflow is rendered.
   * @param key The key passed to [renderChild][com.squareup.workflow.RenderContext.renderChild]
   * when rendering this workflow.
   * @param assertProps A function that performs assertions on the props passed to
   * [renderChild][com.squareup.workflow.RenderContext.renderChild].
   * @param output If non-null, [WorkflowOutput.value] will be "emitted" when this workflow is
   * rendered. The [WorkflowAction] used to handle this output can be verified using methods on
   * [RenderTestResult].
   */
  @OptIn(ExperimentalWorkflowApi::class)
  fun <ChildPropsT, ChildOutputT, ChildRenderingT> expectWorkflow(
    workflowType: KClass<out Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>>,
    rendering: ChildRenderingT,
    key: String = "",
    assertProps: (props: ChildPropsT) -> Unit = {},
    output: WorkflowOutput<ChildOutputT>? = null
  ): RenderTester<PropsT, StateT, OutputT, RenderingT> =
    expectWorkflow(workflowType.workflowIdentifier, rendering, key, output = output, assertProps = {
      @Suppress("UNCHECKED_CAST")
      assertProps(it as ChildPropsT)
    })

  /**
   * Specifies that this render pass is expected to run a particular worker.
   *
   * @param matchesWhen Predicate used to determine if this matches the worker being ran.
   * @param key The key passed to [runningWorker][com.squareup.workflow.RenderContext.runningWorker]
   * when rendering this workflow.
   * @param output If non-null, [WorkflowOutput.value] will be emitted when this worker is ran.
   * The [WorkflowAction] used to handle this output can be verified using methods on
   * [RenderTestResult].
   */
  fun expectWorker(
    matchesWhen: (otherWorker: Worker<*>) -> Boolean,
    key: String = "",
    output: WorkflowOutput<Any?>? = null
  ): RenderTester<PropsT, StateT, OutputT, RenderingT>

  /**
   * Specifies that this render pass is expected to run a particular side effect.
   *
   * @param key The key passed to
   * [runningSideEffect][com.squareup.workflow.RenderContext.runningSideEffect] when rendering this
   * workflow.
   */
  fun expectSideEffect(key: String): RenderTester<PropsT, StateT, OutputT, RenderingT>

  /**
   * Execute the workflow's `render` method and run [block] to perform assertions on and send events
   * to the resulting rendering.
   *
   * All workflows rendered/ran by the workflow must be specified before calling this
   * method. Workers are optionally specified.
   *
   * @param block Passed the result of the render pass to perform assertions on.
   * If no child workflow or worker was configured to emit an output, may also invoke one of the
   * rendering's event handlers. It is an error to invoke an event handler if a child emitted an
   * output.
   * @return A [RenderTestResult] that can be used to verify the [WorkflowAction] that was used to
   * handle a workflow or worker output or a rendering event.
   */
  fun render(block: (rendering: RenderingT) -> Unit = {}): RenderTestResult<PropsT, StateT, OutputT>
}

/**
 * Specifies that this render pass is expected to run a particular worker.
 *
 * @param doesSameWorkAs Worker passed to the actual worker's
 * [doesSameWorkAs][Worker.doesSameWorkAs] method to identify the worker. Note that the actual
 * method is called on the worker instance given by the workflow-under-test, and the value of this
 * argument is passed to that method – if you need custom comparison logic for some reason, use
 * the overload of this method that takes a `matchesWhen` parameter.
 * @param key The key passed to [runningWorker][com.squareup.workflow.RenderContext.runningWorker]
 * when rendering this workflow.
 * @param output If non-null, [WorkflowOutput.value] will be emitted when this worker is ran.
 * The [WorkflowAction] used to handle this output can be verified using methods on
 * [RenderTestResult].
 */
/* ktlint-disable parameter-list-wrapping */
fun <PropsT, StateT, OutputT, RenderingT>
    RenderTester<PropsT, StateT, OutputT, RenderingT>.expectWorker(
  doesSameWorkAs: Worker<*>,
  key: String = "",
  output: WorkflowOutput<Any?>? = null
): RenderTester<PropsT, StateT, OutputT, RenderingT> = expectWorker(
/* ktlint-enable parameter-list-wrapping */
    matchesWhen = { it.doesSameWorkAs(doesSameWorkAs) },
    key = key,
    output = output
)

/**
 * Wrapper around a potentially-nullable [OutputT] value.
 */
@Deprecated(
    "Use WorkflowOutput",
    ReplaceWith("WorkflowOutput<OutputT>", "com.squareup.workflow.WorkflowOutput")
)
typealias EmittedOutput<OutputT> = WorkflowOutput<OutputT>
