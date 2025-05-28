@file:Suppress("ktlint:standard:indent")

package com.squareup.workflow1.testing

import com.squareup.workflow1.ActionApplied
import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.SessionWorkflow
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.WorkflowExperimentalApi
import com.squareup.workflow1.WorkflowIdentifier
import com.squareup.workflow1.WorkflowOutput
import com.squareup.workflow1.config.JvmTestRuntimeConfigTools
import com.squareup.workflow1.identifier
import com.squareup.workflow1.testing.RenderTester.ChildWorkflowMatch
import com.squareup.workflow1.testing.RenderTester.Companion
import com.squareup.workflow1.workflowIdentifier
import kotlinx.coroutines.CoroutineScope
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.isSupertypeOf

/**
 * Create a [RenderTester] to unit test an individual render pass of this workflow, using the
 * workflow's [initial state][StatefulWorkflow.initialState].
 *
 * See [RenderTester] for usage documentation.
 */
@OptIn(WorkflowExperimentalApi::class) // Opt-in is only for the argument check.
@Suppress("UNCHECKED_CAST")
public fun <PropsT, OutputT, RenderingT> Workflow<PropsT, OutputT, RenderingT>.testRender(
  props: PropsT,
  runtimeConfig: RuntimeConfig? = null,
): RenderTester<PropsT, *, OutputT, RenderingT> {
  val statefulWorkflow = asStatefulWorkflow() as StatefulWorkflow<PropsT, Any?, OutputT, RenderingT>
  return statefulWorkflow.testRender(
    props = props,
    runtimeConfig = runtimeConfig,
    initialState = run {
      require(this !is SessionWorkflow<PropsT, *, OutputT, RenderingT>) {
        "Called testRender on a SessionWorkflow without a CoroutineScope. Use the version that passes a CoroutineScope."
      }
      statefulWorkflow.initialState(props, null)
    }
  ) as RenderTester<PropsT, Nothing, OutputT, RenderingT>
}

/**
 * Create a [RenderTester] to unit test an individual render pass of this [SessionWorkflow],
 * using the workflow's [initial state][StatefulWorkflow.initialState], in the [workflowScope].
 *
 * See [RenderTester] for usage documentation.
 */
@OptIn(WorkflowExperimentalApi::class)
@Suppress("UNCHECKED_CAST")
public fun <PropsT, OutputT, RenderingT> SessionWorkflow<PropsT, *, OutputT, RenderingT>.testRender(
  props: PropsT,
  workflowScope: CoroutineScope,
  runtimeConfig: RuntimeConfig? = null,
): RenderTester<PropsT, *, OutputT, RenderingT> {
  val sessionWorkflow: SessionWorkflow<PropsT, Any?, OutputT, RenderingT> =
    asStatefulWorkflow() as SessionWorkflow<PropsT, Any?, OutputT, RenderingT>
  return sessionWorkflow.testRender(
    props = props,
    runtimeConfig = runtimeConfig,
    initialState = sessionWorkflow.initialState(props, null, workflowScope),
  ) as RenderTester<PropsT, Nothing, OutputT, RenderingT>
}

/**
 * Create a [RenderTester] to unit test an individual render pass of this workflow.
 *
 * See [RenderTester] for usage documentation.
 */
public fun <PropsT, StateT, OutputT, RenderingT>
  StatefulWorkflow<PropsT, StateT, OutputT, RenderingT>.testRender(
  props: PropsT,
  initialState: StateT,
  runtimeConfig: RuntimeConfig? = null
): RenderTester<PropsT, StateT, OutputT, RenderingT> =
  RealRenderTester(
    workflow = this,
    props = props,
    state = initialState,
    runtimeConfig = runtimeConfig ?: JvmTestRuntimeConfigTools.getTestRuntimeConfig()
  )

/**
 * The props must be specified, the initial state may be specified, and then all child workflows
 * and workers that are expected to run, and any outputs from them, must be specified with
 * [expectWorkflow] and (optionally) [expectWorker] and [expectSideEffect] calls.
 * If one needs to verify all workers explicitly, perhaps to verify that a worker is *not* run,
 * then use [requireExplicitWorkerExpectations]. Likewise [requireExplicitSideEffectExpectations]
 * for side effects.
 * Then call [render] and perform any assertions on the rendering. An event may also be sent to the
 * rendering if no workflows or workers emitted an output. Lastly, the [RenderTestResult] returned
 * by `render` may be used to assert on the [WorkflowAction]s processed to handle events or outputs
 * by calling [verifyAction][RenderTestResult.verifyAction] or
 * [verifyActionResult][RenderTestResult.verifyActionResult].
 *
 * - All workflows that are rendered/ran by this workflow must be specified.
 * - Workers are optionally specified. Specified workers must run. Unexpected workers on a render
 *   pass do not cause a test failure unless [requireExplicitWorkerExpectations] is true.
 *   Side effects are optionally specified. Specified side effects must run. Unexpected side effects
 *   on a render pass do not cause a test failure unless [requireExplicitSideEffectExpectations] is
 *   true.
 * - It is an error if more than one workflow or worker specifies an output.
 * - It is a test failure if any workflows or workers that were expected were not ran.
 * - It is a test failure if the workflow tried to run any workflows that were not expected.
 * - It is a test failure if no workflow or worker emitted an output, no rendering event was
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
 *    workflow
 *      .testRender(
 *        props = MyProps(…),
 *        initialState = MyState(…)
 *      )
 *      .expectWorker(
 *        workerClass = SubmitLoginWorker::class
 *        key = "signIn",
 *        output = WorkflowOutput(LoginResponse(success = true))
 *      )
 *      .expectWorkflow(
 *        workflowType = ChildWorkflow::class,
 *        key = "child",
 *        assertProps = { assertThat(it.email).isEqualTo("test@foo.com") },
 *        rendering = ChildRendering("message")
 *      )
 *      .render { rendering ->
 *        assertThat(rendering.text).isEqualTo("foo")
 *      }
 *      .verifyAction { action ->
 *        assertThat(action).isEqualTo(Action.CompleteLogin(success = true))
 *      }
 *
 * ### Rendering event
 *
 * This is similar to the example above, but will test an event sent to the rendering instead.
 *
 *    workflow
 *      .testRender(
 *        props = MyProps(…),
 *        initialState = MyState(…)
 *      )
 *      .expectWorker(
 *        matchesWhen = { it is SubmitLoginWorker },
 *        key = "signIn"
 *      )
 *      .expectWorkflow(
 *        workflowType = ChildWorkflow::class,
 *        key = "child",
 *        assertProps = { assertThat(it.email).isEqualTo("test@foo.com") },
 *        rendering = ChildRendering("message")
 *      )
 *      .render { rendering ->
 *        rendering.onCancelClicked()
 *        assertThat(rendering.text).isEqualTo("foo")
 *      }
 *      .verifyAction { action ->
 *        assertThat(action).isEqualTo(Action.CancelLogin)
 *      }
 *
 * ### Verify action result
 *
 * This test verifies the action _result_ instead of the action itself. This technique is useful
 * if the [WorkflowAction] is anonymous or inline.
 *
 *    val currentState = …
 *    val previousState = …
 *
 *    workflow
 *      .testRender(
 *        props = MyProps(…),
 *        initialState = currentState
 *      )
 *      .render { rendering ->
 *        rendering.onCancelClicked()
 *      }
 *      .verifyActionResult { newState, output ->
 *        // Check that the workflow navigated back correctly.
 *        assertThat(newState).isEqualTo(previousState)
 *
 *        // Check that the workflow didn't emit any output from the button click.
 *        assertThat(output).isNull()
 *      }
 *
 * ### Too many outputs
 *
 * This is an example of what **not** to do – this test will error out because a worker is emitting
 * and output _and_ a rendering event is sent.
 *
 *    workflow
 *      .testRender(
 *        props = MyProps(…),
 *        initialState = MyState(…)
 *      )
 *      .expectWorker(
 *        matchesWhen = { it is SubmitLoginWorker },
 *        key = "signIn",
 *        output = WorkflowOutput(LoginResponse(success = true))
 *      )
 *      .expectWorkflow(
 *        workflowType = ChildWorkflow::class,
 *        key = "child",
 *        assertProps = { assertThat(it.email).isEqualTo("test@foo.com") },
 *        rendering = ChildRendering("message")
 *      )
 *      .render { rendering ->
 *        // This will throw and fail the test because the SubmitLoginWorker is also configured to emit
 *        // an output.
 *        rendering.onCancelClicked()
 */
public abstract class RenderTester<PropsT, StateT, OutputT, RenderingT> {
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
  internal abstract fun expectWorkflow(
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
   *
   * @param exactMatch If true, then the test will fail if any other matching expectations are also
   * exact matches, and the expectation will only be allowed to match a single side effect.
   * If false, the match will only be used if no other expectations return exclusive matches (in
   * which case the first match will be used), and the expectation may match multiple side effects.
   *
   * @param matcher A function that is passed the key value from
   * [RenderContext.runningSideEffect][com.squareup.workflow1.BaseRenderContext.runningSideEffect]
   * and return true if this key is expected.
   */
  public abstract fun expectSideEffect(
    description: String,
    exactMatch: Boolean = true,
    matcher: (key: String) -> Boolean
  ): RenderTester<PropsT, StateT, OutputT, RenderingT>

  /**
   * Specifies that this render pass is expected to remember a calculated value with parameters
   * that satisfy [matcher]. This expectation is strict, and will fail if multiple side effects
   * match.
   *
   * @param description String that will be used to describe this expectation in error messages.
   * The description is required since no human-readable description can be derived from the
   * predicate alone.
   *
   * @param exactMatch If true, then the test will fail if any other matching expectations are also
   * exact matches, and the expectation will only be allowed to match a single side effect.
   * If false, the match will only be used if no other expectations return exclusive matches (in
   * which case the first match will be used), and the expectation may match multiple side effects.
   *
   * @param matcher A function that is passed the parameters from
   * [RenderContext.remember][com.squareup.workflow1.BaseRenderContext.remember] and return
   * true if such a call expected.
   */
  public abstract fun expectRemember(
    description: String,
    exactMatch: Boolean = true,
    matcher: (RememberInvocation) -> Boolean
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
   *
   * @return A [RenderTestResult] that can be used to verify the [WorkflowAction] that was used to
   * handle a workflow or worker output or a rendering event.
   */
  public abstract fun render(
    block: (rendering: RenderingT) -> Unit = {}
  ): RenderTestResult<PropsT, StateT, OutputT, RenderingT>

  public abstract fun requireExplicitWorkerExpectations():
    RenderTester<PropsT, StateT, OutputT, RenderingT>

  public abstract fun requireExplicitSideEffectExpectations():
    RenderTester<PropsT, StateT, OutputT, RenderingT>

  public abstract fun requireExplicitRememberExpectations():
    RenderTester<PropsT, StateT, OutputT, RenderingT>

  /**
   * Describes a call to
   * [RenderContext.renderChild][com.squareup.workflow1.BaseRenderContext.renderChild].
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
  public class RenderChildInvocation(
    public val workflow: Workflow<*, *, *>,
    public val props: Any?,
    public val outputType: KTypeProjection,
    public val renderingType: KTypeProjection,
    public val renderKey: String
  )

  /**
   * Captures the parameters of a call to
   * [RenderContext.remember][com.squareup.workflow1.BaseRenderContext.remember].
   */
  public class RememberInvocation(
    public val key: String,
    public val resultType: KType,
    public val inputs: List<Any?>,
  )

  public sealed class ChildWorkflowMatch {
    /**
     * Indicates that the child workflow did not match the predicate and must match a different
     * expectation. The test will fail if all expectations return this value.
     */
    public object NotMatched : ChildWorkflowMatch()

    /**
     * Indicates that the workflow matches the predicate.
     *
     * @param childRendering The value to return as the child's rendering.
     *
     * @param output If non-null, [ActionApplied.output] will be "emitted" when this workflow is
     * rendered. The [WorkflowAction] used to handle this output can be verified using methods on
     * [RenderTestResult].
     */
    public class Matched(
      public val childRendering: Any?,
      public val output: WorkflowOutput<*>? = null
    ) : ChildWorkflowMatch()
  }

  public companion object {
    public const val VERIFY_ALL_LEVELS: Int = -1
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
 * Note that Workflow<Int, String, Int> is *not* a sub-type of Workflow<Int, Object, Int> because
 * it is not covariant for the [OutputT] generic (the same is true for [PropsT]). This means that
 * you cannot use the [WorkflowIdentifier] or [KClass] of a Workflow class whose [OutputT] or
 * [PropsT] are supertypes to the one you want to match. If this is the only reasonable class
 * definition you have access to, then consider using [expectCovariantWorkflow] and specifying
 * those types explicitly.
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
 *    val workflow = Workflow.stateless<…> {
 *      renderChild(
 *        childWorkflow.mapRendering { … }
 *          .mapOutput { … }
 *      )
 *    }
 *
 *    workflow.testRender(…)
 *      .expectWorkflow(childWorkflow::class, …)
 *
 * @param identifier The [WorkflowIdentifier] of the expected workflow. May identify any supertype
 * of the actual rendered workflow, e.g. if the workflow type is an interface and the
 * workflow-under-test injects a fake.
 *
 * @param rendering The rendering to return from
 * [renderChild][com.squareup.workflow1.BaseRenderContext.renderChild] when this workflow is
 * rendered.
 *
 * @param key The key passed to [renderChild][com.squareup.workflow1.BaseRenderContext.renderChild]
 * when rendering this workflow.
 *
 * @param assertProps A function that performs assertions on the props passed to
 * [renderChild][com.squareup.workflow1.BaseRenderContext.renderChild].
 *
 * @param description Optional string that will be used to describe this expectation in error
 * messages.
 */
@Suppress("NOTHING_TO_INLINE")
public inline fun <ChildRenderingT, PropsT, StateT, OutputT, RenderingT>
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
 * Note that Workflow<Int, String, Int> is *not* a sub-type of Workflow<Int, Object, Int> because
 * it is not covariant for the [OutputT] generic (the same is true for [PropsT]). This means that
 * you cannot use the [WorkflowIdentifier] or [KClass] of a Workflow class whose [OutputT] or
 * [PropsT] are supertypes to the one you want to match. If this is the only reasonable class
 * definition you have access to, then consider using [expectCovariantWorkflow] and specifying
 * those types explicitly.
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
 *    val workflow = Workflow.stateless<…> {
 *      renderChild(
 *        childWorkflow.mapRendering { … }
 *          .mapOutput { … }
 *      )
 *    }
 *
 *    workflow.testRender(…)
 *      .expectWorkflow(childWorkflow::class, …)
 *
 * @param identifier The [WorkflowIdentifier] of the expected workflow. May identify any supertype
 * of the actual rendered workflow, e.g. if the workflow type is an interface and the
 * workflow-under-test injects a fake.
 *
 * @param rendering The rendering to return from
 * [renderChild][com.squareup.workflow1.BaseRenderContext.renderChild] when this workflow is
 * rendered.
 *
 * @param key The key passed to [renderChild][com.squareup.workflow1.BaseRenderContext.renderChild]
 * when rendering this workflow.
 *
 * @param assertProps A function that performs assertions on the props passed to
 * [renderChild][com.squareup.workflow1.BaseRenderContext.renderChild].
 *
 * @param output If non-null, [WorkflowOutput.value] will be "emitted" when this workflow is
 * rendered. The [WorkflowAction] used to handle this output can be verified using methods on
 * [RenderTestResult].
 *
 * @param description Optional string that will be used to describe this expectation in error
 * messages.
 */
public fun <ChildOutputT, ChildRenderingT, PropsT, StateT, OutputT, RenderingT>
  RenderTester<PropsT, StateT, OutputT, RenderingT>.expectWorkflow(
  identifier: WorkflowIdentifier,
  rendering: ChildRenderingT,
  output: WorkflowOutput<ChildOutputT>?,
  key: String = "",
  description: String = "",
  assertProps: (props: Any?) -> Unit = {}
): RenderTester<PropsT, StateT, OutputT, RenderingT> = expectWorkflow(
  exactMatch = true,
  description = description.ifBlank {
    "workflow " +
      "identifier=$identifier, " +
      "key=$key, " +
      "rendering=$rendering, " +
      "output=$output"
  }
) { invocation ->
  if (invocation.workflow.identifier.realTypeMatchesClassExpectation(identifier) &&
    invocation.renderKey == key
  ) {
    assertProps(invocation.props)
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
 * Note that Workflow<Int, String, Int> is *not* a sub-type of Workflow<Int, Object, Int> because
 * it is not covariant for the [OutputT] generic (the same is true for [PropsT]). This means that
 * you cannot use the [WorkflowIdentifier] or [KClass] of a Workflow class whose [OutputT] or
 * [PropsT] are supertypes to the one you want to match. If this is the only reasonable class
 * definition you have access to, then consider using [expectCovariantWorkflow] and specifying
 * those types explicitly.
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
 *    val workflow = Workflow.stateless<…> {
 *      renderChild(childWorkflow.mapRendering { … })
 *    }
 *
 *    workflow.testRender(…)
 *      .expectWorkflow(childWorkflow::class, …)
 *
 * @param workflowType The [KClass] of the expected workflow. May also be any of the supertypes
 * of the expected workflow, e.g. if the workflow type is an interface and the workflow-under-test
 * injects a fake. See note above about covariance with [PropsT] and [OutputT] and how these cannot
 * help with supertypes.
 *
 * @param rendering The rendering to return from
 * [renderChild][com.squareup.workflow1.BaseRenderContext.renderChild] when this workflow is
 * rendered.
 *
 * @param key The key passed to [renderChild][com.squareup.workflow1.BaseRenderContext.renderChild]
 * when rendering this workflow.
 *
 * @param assertProps A function that performs assertions on the props passed to
 * [renderChild][com.squareup.workflow1.BaseRenderContext.renderChild].
 *
 * @param output If non-null, [WorkflowOutput.value] will be "emitted" when this workflow is
 * rendered. The [WorkflowAction] used to handle this output can be verified using methods on
 * [RenderTestResult].
 *
 * @param description Optional string that will be used to describe this expectation in error
 * messages.
 */
public inline fun <ChildPropsT, ChildOutputT, ChildRenderingT, PropsT, StateT, OutputT, RenderingT>
  RenderTester<PropsT, StateT, OutputT, RenderingT>.expectWorkflow(
  workflowType: KClass<out Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>>,
  rendering: ChildRenderingT,
  key: String = "",
  crossinline assertProps: (props: ChildPropsT) -> Unit = {},
  output: WorkflowOutput<ChildOutputT>? = null,
  description: String = ""
): RenderTester<PropsT, StateT, OutputT, RenderingT> =
  expectWorkflow(
    workflowType.workflowIdentifier,
    rendering,
    key = key,
    output = output,
    description = description,
    assertProps = {
      @Suppress("UNCHECKED_CAST")
      assertProps(it as ChildPropsT)
    }
  )

/**
 * @see [expectWorkflow] for more on this expectation.
 *
 * This is a special use version for when the only reasonable [KClass] you have to verify against
 * is the definition of a workflow whose [OutputT] and [RenderingT] are supertypes of the [OutputT]
 * and [RenderingT] of the child workflow type you expect to be rendered.
 * In other words, the expected workflow is covariant with the class you have to pass to the
 * expectation. There is a slight nuance here, in that if the [OutputT] is a supertype of the
 * expected child's [OutputT], then those workflow's are not actually covariant since [OutputT]
 * is an invariant generic type. This is not important for the use of this expectation, however.
 *
 * The most often case for this is when you are using a generic factory to construct Workflow
 * instances that you then wish to expect in your test.
 *
 * In that case, use this expectation and provide the [KClass] of the Workflow type, along with the
 * [KType] of the [OutputT] and [RenderingT], the [PropsT] can simply be verified for type safety
 * inside [assertProps] by casting the [Any?] into the expected [PropsT].
 *
 * Note that this implementation does not handle [ImpostorWorkflow][com.squareup.workflow1.ImpostorWorkflow]s
 * (for proxied identifiers) like the other versions do.
 *
 * @param childWorkflowClass The [KClass] of the expected workflow or one of its supertypes,
 * including covariant supertypes. E.g. if the workflow type is an interface and the
 * workflow-under-test injects a fake.
 *
 * @param childOutputType The [KType] of the [OutputT] of the expected child workflow.
 *
 * @param outputTypeVerificationLevel The number of 'levels' of generic arguments to verify in
 * the [OutputT], e.g., for `Wrapper<*>` and level 1 only `Wrapper` would be checked, whereas for
 * level 2, `Wrapper` and `*` (the star projection) would be checked against the
 * [RenderChildInvocation].
 *
 * @param childRenderingType The [KType] of the [RenderingT] of the expected child workflow.
 *
 * @param renderingTypeVerificationLevel The number of 'levels' of generic arguments to verify in
 * the [OutputT], e.g., for `Wrapper<*>` and level 1 only `Wrapper` would be checked, whereas for
 * level 2, `Wrapper` and `*` (the star projection) would be checked against the
 * [RenderChildInvocation].
 *
 * @param rendering The rendering to return from
 * [renderChild][com.squareup.workflow1.BaseRenderContext.renderChild] when this workflow is
 * rendered.
 *
 * @param key The key passed to [renderChild][com.squareup.workflow1.BaseRenderContext.renderChild]
 * when rendering this workflow.
 *
 * @param assertProps A function that performs assertions on the props passed to
 * [renderChild][com.squareup.workflow1.BaseRenderContext.renderChild].
 *
 * @param output If non-null, [WorkflowOutput.value] will be "emitted" when this workflow is
 * rendered. The [WorkflowAction] used to handle this output can be verified using methods on
 * [RenderTestResult].
 *
 * @param description Optional string that will be used to describe this expectation in error
 * messages.
 */
public fun <ChildOutputT, ChildRenderingT, PropsT, StateT, OutputT, RenderingT>
  RenderTester<PropsT, StateT, OutputT, RenderingT>.expectCovariantWorkflow(
  childWorkflowClass: KClass<*>,
  childOutputType: KType,
  outputTypeVerificationLevel: Int = RenderTester.VERIFY_ALL_LEVELS,
  childRenderingType: KType,
  renderingTypeVerificationLevel: Int = Companion.VERIFY_ALL_LEVELS,
  rendering: ChildRenderingT,
  output: WorkflowOutput<ChildOutputT>? = null,
  key: String = "",
  description: String = "",
  assertProps: (props: Any?) -> Unit = {}
): RenderTester<PropsT, StateT, OutputT, RenderingT> = expectWorkflow(
  exactMatch = true,
  description = description.ifBlank {
    "workflow " +
      "workflowClass=$childWorkflowClass, " +
      "childOutputType=$childOutputType, " +
      "childRenderingType=$childRenderingType, " +
      "key=$key, " +
      "rendering=$rendering, " +
      "output=$output"
  }
) { invocation ->
  // Recursive function to verify #n levels of types.
  fun verifyTypesToLevel(
    levels: Int,
    type1: KType,
    type2: KType
  ): Boolean {
    if (levels < 1) return true
    if (levels == 1) {
      // We are at the last level of verification, ignore any further generic type arguments.
      return type1.classifier?.equals(type2.classifier) == true
    } else {
      if (type1.arguments.size != type2.arguments.size) return false
      var acc = true
      type1.arguments.forEachIndexed { index, kTypeProjection1 ->
        val kTypeProjection2 = type2.arguments[index]
        if (kTypeProjection1.type == null || kTypeProjection2.type == null) return false
        acc =
          acc && verifyTypesToLevel(levels - 1, kTypeProjection1.type!!, kTypeProjection2.type!!)
      }
      return acc
    }
  }

  val childClassTypeMatches =
    invocation.workflow.identifier.realTypeMatchesClassExpectation(childWorkflowClass)
  val keyMatches = invocation.renderKey == key
  val outputTypeMatches = invocation.outputType.type?.equals(childOutputType) == true ||
    (
      (outputTypeVerificationLevel > 0 && invocation.outputType.type != null) &&
        verifyTypesToLevel(
          outputTypeVerificationLevel,
          invocation.outputType.type!!,
          childOutputType
        )
      )
  val renderingTypeMatchers = invocation.renderingType.type?.equals(childRenderingType) == true ||
    (
      (renderingTypeVerificationLevel > 0 && invocation.renderingType.type != null) &&
        verifyTypesToLevel(
          renderingTypeVerificationLevel,
          invocation.renderingType.type!!,
          childRenderingType
        )
      )

  if (childClassTypeMatches &&
    keyMatches &&
    outputTypeMatches &&
    renderingTypeMatchers
  ) {
    assertProps(invocation.props)
    ChildWorkflowMatch.Matched(rendering, output)
  } else {
    ChildWorkflowMatch.NotMatched
  }
}

/**
 * Specifies that this render pass is expected to run a particular side effect.
 *
 * @param key The key passed to
 * [runningSideEffect][com.squareup.workflow1.BaseRenderContext.runningSideEffect] when rendering
 * this workflow.
 */
public fun <PropsT, StateT, OutputT, RenderingT>
  RenderTester<PropsT, StateT, OutputT, RenderingT>.expectSideEffect(key: String):
  RenderTester<PropsT, StateT, OutputT, RenderingT> =
  expectSideEffect("side effect with key \"$key\"", exactMatch = true) { it == key }

/**
 * Specifies that this render pass is expected to remember a particular calculated value.
 *
 * @param key The key passed to [remember][com.squareup.workflow1.BaseRenderContext.remember]
 * when rendering this workflow.
 *
 * @param resultType The type of the value returned by the `calculation` function passed
 * to  [remember][com.squareup.workflow1.BaseRenderContext.remember].
 *
 * @param inputs The `inputs` values passed to
 * [remember][com.squareup.workflow1.BaseRenderContext.remember], if any
 *
 * @param assertInputs A function that performs assertions on the inputs passed to
 * [remember][com.squareup.workflow1.BaseRenderContext.remember].
 *
 * @param description Optional string that will be used to describe this expectation in error
 * messages.
 */
public fun <PropsT, StateT, OutputT, RenderingT>
  RenderTester<PropsT, StateT, OutputT, RenderingT>.expectRemember(
  key: String,
  resultType: KType,
  vararg inputs: Any?,
  description: String = "",
  assertInputs: (inputs: List<Any?>) -> Unit = {},
): RenderTester<PropsT, StateT, OutputT, RenderingT> {
  val resolvedDescription = description.ifBlank {
    if (inputs.isNotEmpty()) {
      "remember key=$key, inputs=[${inputs.joinToString(", ")}], resultType=$resultType"
    } else {
      "remember key=$key, resultType=$resultType"
    }
  }
  return expectRemember(
    exactMatch = true,
    description = resolvedDescription,
  ) { invocation ->
    if (resultType.isSupertypeOf(invocation.resultType) &&
      inputs.toList() == invocation.inputs &&
      key == invocation.key
    ) {
      assertInputs(invocation.inputs)
      true
    } else {
      false
    }
  }
}
