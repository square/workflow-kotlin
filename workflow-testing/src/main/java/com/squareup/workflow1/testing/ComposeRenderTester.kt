package com.squareup.workflow1.testing

import androidx.compose.runtime.Composable
import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.WorkflowExperimentalApi
import com.squareup.workflow1.WorkflowIdentifier
import com.squareup.workflow1.WorkflowOutput
import com.squareup.workflow1.compose.ComposeWorkflow
import com.squareup.workflow1.compose.LocalWorkflowComposableRenderer
import com.squareup.workflow1.compose.ComposeWorkflowInterceptor
import com.squareup.workflow1.compose.internal._DO_NOT_USE_invokeComposeWorkflowProduceRendering
import com.squareup.workflow1.identifier
import com.squareup.workflow1.internal.compose.ComposeWorkflowState
import com.squareup.workflow1.internal.compose.runtime.launchSynchronizedMolecule
import com.squareup.workflow1.compose.internal.withCompositionLocals
import com.squareup.workflow1.testing.RealRenderTester.Expectation
import com.squareup.workflow1.testing.RealRenderTester.Expectation.ExpectedWorkflow
import com.squareup.workflow1.testing.RenderTester.ChildWorkflowMatch.Matched
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel

// TODO move this to RealComposeRenderTester
@OptIn(WorkflowExperimentalApi::class)
internal class ComposeRenderTester<PropsT, OutputT, RenderingT>(
  private val workflow: ComposeWorkflow<PropsT, OutputT, RenderingT>,
  private val props: PropsT,
  private val runtimeConfig: RuntimeConfig,
) : RenderTester<PropsT, ComposeWorkflowState, OutputT, RenderingT>(),
  ComposeWorkflowInterceptor,
  RenderTestResult<PropsT, ComposeWorkflowState, OutputT, RenderingT> {

  private data class OutputWithHandler<ChildOutputT>(
    val output: ChildOutputT,
    val handler: (ChildOutputT) -> Unit
  )

  /**
   * List of [Expectation]s that are expected when the workflow is rendered. New expectations are
   * registered into this list. Once the render pass has started, expectations are moved from this
   * list to [consumedExpectations] as soon as they're matched.
   */
  private val expectations: MutableList<ExpectedWorkflow> = mutableListOf()

  /**
   * Empty until the render pass starts, then every time the workflow matches an expectation that
   * has `exactMatch` set to true, it is moved from [expectations] to this list.
   */
  private val consumedExpectations: MutableList<Expectation<*>> = mutableListOf()

  private var processedOutputHandler: OutputWithHandler<*>? = null

  /**
   * Tracks the identifier/key pairs of all calls to [renderWorkflow], so it can emulate the behavior
   * of the real runtime and throw if a workflow is rendered twice in the same pass.
   */
  private val renderedChildren: MutableList<WorkflowIdentifier> = mutableListOf()

  override fun expectWorkflow(
    description: String,
    exactMatch: Boolean,
    matcher: (RenderChildInvocation) -> ChildWorkflowMatch
  ): RenderTester<PropsT, ComposeWorkflowState, OutputT, RenderingT> = apply {
    expectations += ExpectedWorkflow(matcher, exactMatch, description)
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
    val emitOutput: (OutputT) -> Unit = { output ->
      TODO()
    }

    val scope = CoroutineScope(Dispatchers.Unconfined)
    try {
      val molecule = scope.launchSynchronizedMolecule(onNeedsRecomposition = {})
      val rendering = molecule.recomposeWithContent {
        withCompositionLocals(LocalWorkflowComposableRenderer provides this) {
          _DO_NOT_USE_invokeComposeWorkflowProduceRendering(workflow, props, emitOutput)
        }
      }
      block(rendering)
    } finally {
      scope.cancel()
    }
    return this
  }

  @Composable
  override fun <ChildPropsT, ChildOutputT, ChildRenderingT> renderChild(
    childWorkflow: Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
    props: ChildPropsT,
    onOutput: ((ChildOutputT) -> Unit)?
  ): ChildRenderingT {
    val identifier = childWorkflow.identifier
    require(identifier !in renderedChildren) {
      "Expected keys to be unique for ${childWorkflow.identifier}"
    }
    renderedChildren += identifier

    val description = buildString {
      append("child ")
      append(childWorkflow.identifier)
      // if (key.isNotEmpty()) {
      //   append(" with key \"$key\"")
      // }
    }
    val invocation = createRenderChildInvocation(childWorkflow, props, renderKey = "")
    val matches = expectations.mapNotNull {
      val matchResult = it.matcher(invocation)
      if (matchResult is Matched) Pair(it, matchResult) else null
    }
    if (matches.isEmpty()) {
      throw AssertionError("Tried to render unexpected $description")
    }

    val exactMatches = matches.filter { it.first.exactMatch }
    val (_, match) = when {
      exactMatches.size == 1 -> {
        exactMatches.single()
          .also { (expected, _) ->
            expectations -= expected
            consumedExpectations += expected
          }
      }

      exactMatches.size > 1 -> {
        throw AssertionError(
          "Multiple expectations matched $description:\n" +
            exactMatches.joinToString(separator = "\n") { "  ${it.first.describe()}" }
        )
      }
      // Inexact matches are not consumable.
      else -> matches.first()
    }

    if (match.output != null) {
      check(processedOutputHandler == null) {
        "Expected only one output to be expected: $description expected to emit " +
          "${match.output.value} but ${emittedOutput?.debuggingName} was already processed."
      }
      processedOutputHandler = OutputWithHandler(match.output, onOutput)
      @Suppress("UNCHECKED_CAST")
      processedAction = handler(match.output.value as ChildOutputT)
    }

    @Suppress("UNCHECKED_CAST")
    return match.childRendering as ChildRenderingT
  }

  override fun verifyAction(
    block: (WorkflowAction<PropsT, ComposeWorkflowState, OutputT>) -> Unit
  ): RenderTestResult<PropsT, ComposeWorkflowState, OutputT, RenderingT> {
    TODO("Not yet implemented")
  }

  override fun verifyActionResult(
    block: (newState: ComposeWorkflowState, appliedResult: WorkflowOutput<OutputT>?) -> Unit
  ): RenderTestResult<PropsT, ComposeWorkflowState, OutputT, RenderingT> {
    TODO("Not yet implemented")
  }

  override fun testNextRender(): RenderTester<PropsT, ComposeWorkflowState, OutputT, RenderingT> =
    testNextRenderWithProps(props)

  override fun testNextRenderWithProps(
    newProps: PropsT
  ): RenderTester<PropsT, ComposeWorkflowState, OutputT, RenderingT> {
    TODO("Not yet implemented")
  }
}
