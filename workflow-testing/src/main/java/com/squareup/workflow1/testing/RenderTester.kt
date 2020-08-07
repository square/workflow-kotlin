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
package com.squareup.workflow1.testing

import com.squareup.workflow1.ExperimentalWorkflowApi
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.WorkflowIdentifier
import com.squareup.workflow1.WorkflowOutput
import com.squareup.workflow1.identifier
import com.squareup.workflow1.testing.RenderTester.ChildWorkflowMatch
import com.squareup.workflow1.workflowIdentifier
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.KTypeProjection

@Deprecated(
    "Renamed to testRender",
    ReplaceWith("testRender(props)", "com.squareup.workflow1.testing.testRender")
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
        "com.squareup.workflow1.testing.testRender"
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
   * @param description String that will be used to describe this expectation in error messages.
   * The description is required since no human-readable description can be derived from the
   * predicate alone.
   * @param exactMatch If true, then the test will fail if any other matching expectations are also
   * exact matches, and the expectation will only be allowed to match a single child workflow.
   * If false, the match will only be used if no other expectations return exclusive matches (in
   * which case the first match will be used), and the expectation may match multiple children.
   * @param matcher A function that determines whether a given [RenderChildInvocation] matches this
   * expectation by returning a [ChildWorkflowMatch]. If the expectation matches, the function
   * must include the rendering and optional output for the child workflow.
   */
  fun expectWorkflow(
    description: String,
    exactMatch: Boolean = true,
    matcher: (RenderChildInvocation) -> ChildWorkflowMatch
  ): RenderTester<PropsT, StateT, OutputT, RenderingT>

  /**
   * Specifies that this render pass is expected to run a side effect with a key that satisfies
   * [matcher]. This expectation is strict, and will fail if multiple side effects match.
   *
   * @param description String that will be used to describe this expectation in error messages.
   * The description is required since no human-readable description can be derived from the
   * predicate alone.
   * @param exactMatch If true, then the test will fail if any other matching expectations are also
   * exact matches, and the expectation will only be allowed to match a single side effect.
   * If false, the match will only be used if no other expectations return exclusive matches (in
   * which case the first match will be used), and the expectation may match multiple side effects.
   * @param matcher A function that is passed the key value from
   * [runningSideEffect][com.squareup.workflow1.RenderContext.runningSideEffect] and return true if
   * this key is expected.
   */
  fun expectSideEffect(
    description: String,
    exactMatch: Boolean = true,
    matcher: (key: String) -> Boolean
  ): RenderTester<PropsT, StateT, OutputT, RenderingT>

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

  /**
   * Describes a call to
   * [RenderContext.renderChild][com.squareup.workflow1.RenderContext.renderChild].
   *
   * ## Output and rendering types
   *
   * The testing library will attempt to determine the output and rendering types by using
   * reflection to determine the type arguments that the concrete workflow class passes to the
   * [Workflow] interface. This is subject to the limitations of Kotlin's reflection. Notably, there
   * is a compiler bug ([KT-17103](https://youtrack.jetbrains.com/issue/KT-17103)) that prevents
   * reflecting on these types when the workflow is an anonymous class that was created by an inline
   * function with reified types, such as `Workflow.stateful` and `Workflow.stateless`.
   *
   * @param workflow The child workflow that is being rendered.
   * @param props The props value passed to `renderChild`.
   * @param outputType The [KType] of the workflow's `OutputT`.
   * @param renderingType The [KType] of the workflow's `RenderingT`.
   * @param renderKey The string key passed to `renderChild`.
   */
  class RenderChildInvocation(
    val workflow: Workflow<*, *, *>,
    val props: Any?,
    val outputType: KTypeProjection,
    val renderingType: KTypeProjection,
    val renderKey: String
  )

  sealed class ChildWorkflowMatch {
    /**
     * Indicates that the child workflow did not match the predicate and must match a different
     * expectation. The test will fail if all expectations return this value.
     */
    object NotMatched : ChildWorkflowMatch()

    /**
     * Indicates that the workflow matches the predicate.
     *
     * @param childRendering The value to return as the child's rendering.
     * @param output If non-null, [WorkflowOutput.value] will be "emitted" when this workflow is
     * rendered. The [WorkflowAction] used to handle this output can be verified using methods on
     * [RenderTestResult].
     */
    class Matched(
      val childRendering: Any?,
      val output: WorkflowOutput<Any?>? = null
    ) : ChildWorkflowMatch()
  }
}

/**
 * Specifies that this render pass is expected to render a particular child workflow.
 *
 * Workflow identifiers are compared taking the type hierarchy into account. When a workflow is
 * rendered, it will match any expectation that specifies the type of that workflow, or any of
 * its supertypes. This means that if you have a workflow that is split into an interface and a
 * concrete class, your render tests can pass the class of the interface to this method instead of
 * the actual class that implements it.
 *
 * ## Expecting impostor workflows
 *
 * If the workflow-under-test renders an
 * [ImpostorWorkflow][com.squareup.workflow1.ImpostorWorkflow], the match will not be performed
 * using the impostor type, but rather the
 * [real identifier][WorkflowIdentifier.getRealIdentifierType] of the impostor's
 * [WorkflowIdentifier]. This will be the last identifier in the chain of impostor workflows'
 * [realIdentifier][com.squareup.workflow1.ImpostorWorkflow.realIdentifier]s.
 *
 * A workflow that is wrapped multiple times by various operators will be matched on the upstream
 * workflow, so for example the following expectation would succeed:
 *
 * ```
 * val workflow = Workflow.stateless<…> {
 *   renderChild(
 *     childWorkflow.mapRendering { … }
 *       .mapOutput { … }
 *   )
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
 * [renderChild][com.squareup.workflow1.RenderContext.renderChild] when this workflow is rendered.
 * @param key The key passed to [renderChild][com.squareup.workflow1.RenderContext.renderChild]
 * when rendering this workflow.
 * @param assertProps A function that performs assertions on the props passed to
 * [renderChild][com.squareup.workflow1.RenderContext.renderChild].
 * @param output If non-null, [WorkflowOutput.value] will be "emitted" when this workflow is
 * rendered. The [WorkflowAction] used to handle this output can be verified using methods on
 * [RenderTestResult].
 * @param description Optional string that will be used to describe this expectation in error
 * messages.
 */
@Suppress("NOTHING_TO_INLINE")
@ExperimentalWorkflowApi
/* ktlint-disable parameter-list-wrapping */
inline fun <ChildRenderingT, PropsT, StateT, OutputT, RenderingT>
    RenderTester<PropsT, StateT, OutputT, RenderingT>.expectWorkflow(
  identifier: WorkflowIdentifier,
  rendering: ChildRenderingT,
  key: String = "",
  description: String = "",
  noinline assertProps: (props: Any?) -> Unit = {}
): RenderTester<PropsT, StateT, OutputT, RenderingT> =
  expectWorkflow(identifier, rendering, null as WorkflowOutput<*>?, key, description, assertProps)

/**
 * Specifies that this render pass is expected to render a particular child workflow.
 *
 * Workflow identifiers are compared taking the type hierarchy into account. When a workflow is
 * rendered, it will match any expectation that specifies the type of that workflow, or any of
 * its supertypes. This means that if you have a workflow that is split into an interface and a
 * concrete class, your render tests can pass the class of the interface to this method instead of
 * the actual class that implements it.
 *
 * ## Expecting impostor workflows
 *
 * If the workflow-under-test renders an
 * [ImpostorWorkflow][com.squareup.workflow1.ImpostorWorkflow], the match will not be performed
 * using the impostor type, but rather the
 * [real identifier][WorkflowIdentifier.getRealIdentifierType] of the impostor's
 * [WorkflowIdentifier]. This will be the last identifier in the chain of impostor workflows'
 * [realIdentifier][com.squareup.workflow1.ImpostorWorkflow.realIdentifier]s.
 *
 * A workflow that is wrapped multiple times by various operators will be matched on the upstream
 * workflow, so for example the following expectation would succeed:
 *
 * ```
 * val workflow = Workflow.stateless<…> {
 *   renderChild(
 *     childWorkflow.mapRendering { … }
 *       .mapOutput { … }
 *   )
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
 * [renderChild][com.squareup.workflow1.RenderContext.renderChild] when this workflow is rendered.
 * @param key The key passed to [renderChild][com.squareup.workflow1.RenderContext.renderChild]
 * when rendering this workflow.
 * @param assertProps A function that performs assertions on the props passed to
 * [renderChild][com.squareup.workflow1.RenderContext.renderChild].
 * @param output If non-null, [WorkflowOutput.value] will be "emitted" when this workflow is
 * rendered. The [WorkflowAction] used to handle this output can be verified using methods on
 * [RenderTestResult].
 * @param description Optional string that will be used to describe this expectation in error
 * messages.
 */
@ExperimentalWorkflowApi
/* ktlint-disable parameter-list-wrapping */
fun <ChildOutputT, ChildRenderingT, PropsT, StateT, OutputT, RenderingT>
    RenderTester<PropsT, StateT, OutputT, RenderingT>.expectWorkflow(
  identifier: WorkflowIdentifier,
  rendering: ChildRenderingT,
  output: WorkflowOutput<ChildOutputT>?,
  key: String = "",
  description: String = "",
  assertProps: (props: Any?) -> Unit = {}
): RenderTester<PropsT, StateT, OutputT, RenderingT> = expectWorkflow(
/* ktlint-enable parameter-list-wrapping */
    exactMatch = true,
    description = description.ifBlank {
      "workflow " +
          "identifier=$identifier, " +
          "key=$key, " +
          "rendering=$rendering, " +
          "output=$output"
    }
) {
  if (it.workflow.identifier.realTypeMatchesExpectation(identifier) &&
      it.renderKey == key
  ) {
    assertProps(it.props)
    ChildWorkflowMatch.Matched(rendering, output)
  } else {
    ChildWorkflowMatch.NotMatched
  }
}

/**
 * Specifies that this render pass is expected to render a particular child workflow.
 *
 * Workflow identifiers are compared taking the type hierarchy into account. When a workflow is
 * rendered, it will match any expectation that specifies the type of that workflow, or any of
 * its supertypes. This means that if you have a workflow that is split into an interface and a
 * concrete class, your render tests can pass the class of the interface to this method instead of
 * the actual class that implements it.
 *
 * ## Expecting impostor workflows
 *
 * If the workflow-under-test renders an
 * [ImpostorWorkflow][com.squareup.workflow1.ImpostorWorkflow], the match will not be performed
 * using the impostor type, but rather the
 * [real identifier][WorkflowIdentifier.getRealIdentifierType] of the impostor's
 * [WorkflowIdentifier]. This will be the last identifier in the chain of impostor workflows'
 * [realIdentifier][com.squareup.workflow1.ImpostorWorkflow.realIdentifier]s.
 *
 * A workflow that is wrapped multiple times by various operators will be matched on the upstream
 * workflow, so for example the following expectation would succeed:
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
 * [renderChild][com.squareup.workflow1.RenderContext.renderChild] when this workflow is rendered.
 * @param key The key passed to [renderChild][com.squareup.workflow1.RenderContext.renderChild]
 * when rendering this workflow.
 * @param assertProps A function that performs assertions on the props passed to
 * [renderChild][com.squareup.workflow1.RenderContext.renderChild].
 * @param output If non-null, [WorkflowOutput.value] will be "emitted" when this workflow is
 * rendered. The [WorkflowAction] used to handle this output can be verified using methods on
 * [RenderTestResult].
 * @param description Optional string that will be used to describe this expectation in error
 * messages.
 */
@OptIn(ExperimentalWorkflowApi::class)
/* ktlint-disable parameter-list-wrapping */
inline fun <ChildPropsT, ChildOutputT, ChildRenderingT, PropsT, StateT, OutputT, RenderingT>
    RenderTester<PropsT, StateT, OutputT, RenderingT>.expectWorkflow(
  workflowType: KClass<out Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>>,
  rendering: ChildRenderingT,
  key: String = "",
  crossinline assertProps: (props: ChildPropsT) -> Unit = {},
  output: WorkflowOutput<ChildOutputT>? = null,
  description: String = ""
): RenderTester<PropsT, StateT, OutputT, RenderingT> =
/* ktlint-enable parameter-list-wrapping */
  expectWorkflow(
      workflowType.workflowIdentifier, rendering, key = key, output = output,
      description = description,
      assertProps = {
        @Suppress("UNCHECKED_CAST")
        assertProps(it as ChildPropsT)
      })

/**
 * Specifies that this render pass is expected to run a particular side effect.
 *
 * @param key The key passed to
 * [runningSideEffect][com.squareup.workflow1.RenderContext.runningSideEffect] when rendering this
 * workflow.
 */
/* ktlint-disable parameter-list-wrapping */
fun <PropsT, StateT, OutputT, RenderingT>
    RenderTester<PropsT, StateT, OutputT, RenderingT>.expectSideEffect(key: String):
    RenderTester<PropsT, StateT, OutputT, RenderingT> =
/* ktlint-enable parameter-list-wrapping */
  expectSideEffect("side effect with key \"$key\"", exactMatch = true) { it == key }

@Deprecated(
    "Use WorkflowOutput",
    ReplaceWith(
        "WorkflowOutput",
        "com.squareup.workflow1.WorkflowOutput"
    )
)
typealias EmittedOutput<OutputT> = WorkflowOutput<OutputT>
