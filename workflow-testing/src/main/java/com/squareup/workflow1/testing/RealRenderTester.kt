package com.squareup.workflow1.testing

import com.squareup.workflow1.BaseRenderContext
import com.squareup.workflow1.RenderContext
import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.Sink
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.Worker
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.WorkflowAction.Companion.noAction
import com.squareup.workflow1.WorkflowIdentifier
import com.squareup.workflow1.WorkflowIdentifierType
import com.squareup.workflow1.WorkflowIdentifierType.Snapshottable
import com.squareup.workflow1.WorkflowIdentifierType.Unsnapshottable
import com.squareup.workflow1.WorkflowOutput
import com.squareup.workflow1.WorkflowTracer
import com.squareup.workflow1.applyTo
import com.squareup.workflow1.identifier
import com.squareup.workflow1.testing.RealRenderTester.Expectation
import com.squareup.workflow1.testing.RealRenderTester.Expectation.ExpectedRemember
import com.squareup.workflow1.testing.RealRenderTester.Expectation.ExpectedSideEffect
import com.squareup.workflow1.testing.RealRenderTester.Expectation.ExpectedWorker
import com.squareup.workflow1.testing.RealRenderTester.Expectation.ExpectedWorkflow
import com.squareup.workflow1.testing.RenderTester.ChildWorkflowMatch.Matched
import com.squareup.workflow1.testing.RenderTester.RenderChildInvocation
import kotlinx.coroutines.CoroutineScope
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.allSupertypes
import kotlin.reflect.full.isSuperclassOf
import kotlin.reflect.full.isSupertypeOf

private const val WORKFLOW_INTERFACE_NAME = "com.squareup.workflow1.Workflow"

internal class RealRenderTester<PropsT, StateT, OutputT, RenderingT>(
  private val workflow: StatefulWorkflow<PropsT, StateT, OutputT, RenderingT>,
  private val props: PropsT,
  private val state: StateT,
  override val runtimeConfig: RuntimeConfig,
  /**
   * List of [Expectation]s that are expected when the workflow is rendered. New expectations are
   * registered into this list. Once the render pass has started, expectations are moved from this
   * list to [consumedExpectations] as soon as they're matched.
   */
  private val expectations: MutableList<Expectation<*>> = mutableListOf(),
  /**
   * Empty until the render pass starts, then every time the workflow matches an expectation that
   * has `exactMatch` set to true, it is moved from [expectations] to this list.
   */
  private val consumedExpectations: MutableList<Expectation<*>> = mutableListOf(),
  /**
   * Flag that is set as soon as an expectation is registered that emits an output.
   */
  private var childWillEmitOutput: Boolean = false,
  /**
   * If an expectation includes a [WorkflowOutput], then when that expectation is matched, this
   * property stores the [WorkflowAction] that was specified to handle that output.
   */
  private var processedAction: WorkflowAction<PropsT, StateT, OutputT>? = null,
) : RenderTester<PropsT, StateT, OutputT, RenderingT>(),
  BaseRenderContext<PropsT, StateT, OutputT>,
  RenderTestResult<PropsT, StateT, OutputT, RenderingT>,
  Sink<WorkflowAction<PropsT, StateT, OutputT>> {

  internal sealed class Expectation<OutputT> {
    abstract fun describe(): String

    open val output: WorkflowOutput<OutputT>? = null

    data class ExpectedWorkflow(
      val matcher: (RenderChildInvocation) -> ChildWorkflowMatch,
      val exactMatch: Boolean,
      val description: String
    ) : Expectation<Any?>() {
      override fun describe(): String = description
    }

    data class ExpectedWorker<OutputT>(
      val matchesWhen: (otherWorker: Worker<*>) -> Boolean,
      val key: String,
      override val output: WorkflowOutput<OutputT>?,
      val description: String
    ) : Expectation<OutputT>() {
      override fun describe(): String = description.ifBlank { "worker key=$key, output=$output" }
    }

    data class ExpectedSideEffect(
      val matcher: (String) -> Boolean,
      val exactMatch: Boolean,
      val description: String
    ) : Expectation<Nothing>() {
      override fun describe(): String = description
    }

    data class ExpectedRemember(
      val matcher: (RememberInvocation) -> RememberMatch,
      val exactMatch: Boolean,
      val description: String,
    ) : Expectation<Nothing>() {
      override fun describe(): String = description
    }
  }

  private var frozen = false

  private var explicitWorkerExpectationsRequired: Boolean = false
  private var explicitSideEffectExpectationsRequired: Boolean = false
  private val stateAndOutput: Pair<StateT, WorkflowOutput<OutputT>?> by lazy {
    val action = processedAction ?: noAction()
    val (state, actionApplied) = action.applyTo(props, state)
    state to actionApplied.output
  }

  /**
   * Tracks the identifier/key pairs of all calls to [renderChild], so it can emulate the behavior
   * of the real runtime and throw if a workflow is rendered twice in the same pass.
   */
  private val renderedChildren: MutableList<Pair<WorkflowIdentifier, String>> = mutableListOf()

  /**
   * Tracks the keys of all calls to [runningSideEffect], so it can emulate the behavior of the real
   * runtime and throw if duplicate keys are found.
   */
  private val runSideEffects: MutableList<String> = mutableListOf()

  /**
   * Tracks the invocations all calls to [remember], so it can emulate the behavior
   * of the real runtime and throw if duplicate keys are found.
   */
  private val rememberSet = mutableSetOf<RememberInvocation>()

  override val actionSink: Sink<WorkflowAction<PropsT, StateT, OutputT>> get() = this
  override val workflowTracer: WorkflowTracer? = null

  override fun expectWorkflow(
    description: String,
    exactMatch: Boolean,
    matcher: (RenderChildInvocation) -> ChildWorkflowMatch
  ): RenderTester<PropsT, StateT, OutputT, RenderingT> = apply {
    expectations += ExpectedWorkflow(matcher, exactMatch, description)
  }

  override fun expectSideEffect(
    description: String,
    exactMatch: Boolean,
    matcher: (key: String) -> Boolean
  ): RenderTester<PropsT, StateT, OutputT, RenderingT> = apply {
    expectations += ExpectedSideEffect(matcher, exactMatch, description)
  }

  override fun expectRemember(
    description: String,
    exactMatch: Boolean,
    matcher: (RememberInvocation) -> RememberMatch
  ): RenderTester<PropsT, StateT, OutputT, RenderingT> = apply {
    expectations += ExpectedRemember(matcher, exactMatch, description)
  }

  override fun render(
    block: (RenderingT) -> Unit
  ): RenderTestResult<PropsT, StateT, OutputT, RenderingT> {
    if (!explicitWorkerExpectationsRequired) {
      // Allow unexpected workers.
      expectWorker(description = "unexpected worker", exactMatch = false) { _, _, _ -> true }
    }

    if (!explicitSideEffectExpectationsRequired) {
      // Allow unexpected side effects.
      expectSideEffect(description = "unexpected side effect", exactMatch = false) { true }
    }

    frozen = false
    // Clone the expectations to run a "dry" render pass.
    val noopContext = deepCloneForRender()
    workflow.render(props, state, RenderContext(noopContext, workflow))
    val rendering = workflow.render(props, state, RenderContext(this, workflow))
    frozen = true
    block(rendering)

    // Ensure all exact matches were consumed.
    val unconsumedExactMatches = expectations.filter {
      when (it) {
        is ExpectedWorkflow -> it.exactMatch
        // Workers are always exact matches.
        is ExpectedWorker -> true
        is ExpectedSideEffect -> it.exactMatch
        is ExpectedRemember -> it.exactMatch
      }
    }
    if (unconsumedExactMatches.isNotEmpty()) {
      throw AssertionError(
        "Expected ${unconsumedExactMatches.size} more workflows, workers, " +
          "side effects, or remembers to be run:\n" +
          unconsumedExactMatches.joinToString(separator = "\n") { "  ${it.describe()}" }
      )
    }

    return this
  }

  override fun <ChildPropsT, ChildOutputT, ChildRenderingT> renderChild(
    child: Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
    props: ChildPropsT,
    key: String,
    handler: (ChildOutputT) -> WorkflowAction<PropsT, StateT, OutputT>
  ): ChildRenderingT {
    checkNotFrozen { "renderChild(${child.identifier})" }
    val identifierPair = Pair(child.identifier, key)
    require(identifierPair !in renderedChildren) {
      "Expected keys to be unique for ${child.identifier}: key=\"$key\""
    }
    renderedChildren += identifierPair

    val description = buildString {
      append("child ")
      append(child.identifier)
      if (key.isNotEmpty()) {
        append(" with key \"$key\"")
      }
    }
    val invocation = createRenderChildInvocation(child, props, key)
    val matches = expectations.filterIsInstance<ExpectedWorkflow>()
      .mapNotNull {
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
      check(processedAction == null) {
        "Expected only one output to be expected: $description expected to emit " +
          "${match.output.value} but ${processedAction?.debuggingName} was already processed."
      }
      @Suppress("UNCHECKED_CAST")
      processedAction = handler(match.output.value as ChildOutputT)
    }

    @Suppress("UNCHECKED_CAST")
    return match.childRendering as ChildRenderingT
  }

  override fun runningSideEffect(
    key: String,
    sideEffect: suspend CoroutineScope.() -> Unit
  ) {
    checkNotFrozen { "runningSideEffect($key)" }
    require(key !in runSideEffects) { "Expected side effect keys to be unique: \"$key\"" }
    runSideEffects += key

    val description = "side effect with key \"$key\""

    val matches = expectations.filterIsInstance<ExpectedSideEffect>()
      .mapNotNull {
        if (it.matcher(key)) it else null
      }
    if (matches.isEmpty()) {
      throw AssertionError("Tried to run unexpected $description")
    }
    val exactMatches = matches.filter { it.exactMatch }

    if (exactMatches.size > 1) {
      throw AssertionError(
        "Multiple expectations matched $description:\n" +
          matches.joinToString(separator = "\n") { "  ${it.describe()}" }
      )
    }

    // Inexact matches are not consumable.
    exactMatches.singleOrNull()
      ?.let { expected ->
        expectations -= expected
        consumedExpectations += expected
      }
  }

  override fun <ResultT> remember(
    key: String,
    resultType: KType,
    vararg inputs: Any?,
    calculation: () -> ResultT
  ): ResultT {
    checkNotFrozen { "remember($key)" }
    val invocation = RememberInvocation(key, resultType, inputs.asList())
    check(rememberSet.add(invocation)) {
      "Expected combination of key, inputs and result type to be unique: \"$key\""
    }

    val description = "remember with key \"$key\""

    val matches = expectations.filterIsInstance<ExpectedRemember>()
      .mapNotNull {
        val matchResult = it.matcher(invocation)
        if (matchResult is RememberMatch.Matched) Pair(it, matchResult) else null
      }
    if (matches.isEmpty()) {
      throw AssertionError("Unexpected $description")
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

    @Suppress("UNCHECKED_CAST")
    return match.result as ResultT
  }

  override fun requireExplicitWorkerExpectations():
    RenderTester<PropsT, StateT, OutputT, RenderingT> = this.apply {
    explicitWorkerExpectationsRequired = true
  }

  override fun requireExplicitSideEffectExpectations():
    RenderTester<PropsT, StateT, OutputT, RenderingT> = this.apply {
    explicitSideEffectExpectationsRequired = true
  }

  override fun send(value: WorkflowAction<PropsT, StateT, OutputT>) {
    if (!frozen) {
      throw UnsupportedOperationException(
        "Expected sink to not be sent to until after the render pass. " +
          "Received action: ${value.debuggingName}"
      )
    }
    checkNoOutputs()
    check(processedAction == null) {
      "Tried to send action to sink after another action was already processed:\n" +
        "  processed action=${processedAction?.debuggingName}\n" +
        "  attempted action=${value.debuggingName}"
    }
    processedAction = value
  }

  override fun verifyAction(
    block: (WorkflowAction<PropsT, StateT, OutputT>) -> Unit
  ): RenderTestResult<PropsT, StateT, OutputT, RenderingT> {
    val action = processedAction ?: noAction()
    block(action)
    return this
  }

  override fun verifyActionResult(
    block: (newState: StateT, output: WorkflowOutput<OutputT>?) -> Unit
  ): RenderTestResult<PropsT, StateT, OutputT, RenderingT> {
    return verifyAction {
      val (state, output) = stateAndOutput
      block(state, output)
    }
  }

  override fun testNextRender(): RenderTester<PropsT, StateT, OutputT, RenderingT> =
    testNextRenderWithProps(props)

  override fun testNextRenderWithProps(
    newProps: PropsT
  ): RenderTester<PropsT, StateT, OutputT, RenderingT> {
    val (stateAfterRender, _) = stateAndOutput
    val newState = if (props != newProps) {
      workflow.onPropsChanged(props, newProps, stateAfterRender)
    } else {
      stateAfterRender
    }
    return RealRenderTester(
      workflow = workflow,
      props = newProps,
      state = newState,
      runtimeConfig = runtimeConfig
    )
  }

  private fun deepCloneForRender(): BaseRenderContext<PropsT, StateT, OutputT> = RealRenderTester(
    workflow,
    props,
    state,
    runtimeConfig = runtimeConfig,
    // Copy the list of expectations since it's mutable.
    expectations = ArrayList(expectations),
    // Don't care about consumed expectations.
    childWillEmitOutput = childWillEmitOutput,
    processedAction = processedAction
  )

  private fun checkNoOutputs(newExpectation: Expectation<*>? = null) {
    check(!childWillEmitOutput) {
      val expectationsWithOutputs = (expectations + listOfNotNull(newExpectation))
        .filter { it.output != null }
      "Expected only one child to emit an output:\n" +
        expectationsWithOutputs.joinToString(separator = "\n") { "  $it" }
    }
  }

  private fun checkNotFrozen(reason: () -> String = { "" }) = check(!frozen) {
    "RenderContext cannot be used after render method returns" +
      "${reason().takeUnless { it.isBlank() }?.let { " ($it)" }}"
  }
}

internal fun createRenderChildInvocation(
  workflow: Workflow<*, *, *>,
  props: Any?,
  renderKey: String
): RenderChildInvocation {
  val workflowClass = workflow::class

  // Get the KType of the Workflow interface with the type parameters specified by this workflow
  // instance.
  val workflowInterfaceType = workflowClass.allSupertypes
    .single { type ->
      (type.classifier as? KClass<*>)
        ?.let { it.qualifiedName == WORKFLOW_INTERFACE_NAME }
        ?: false
    }

  check(workflowInterfaceType.arguments.size == 3)
  val (_, outputType, renderingType) = workflowInterfaceType.arguments
  return RenderChildInvocation(workflow, props, outputType, renderingType, renderKey)
}

/**
 * Returns true iff this identifier's [WorkflowIdentifier.getRealIdentifierType] is the same type as
 * or a subtype of [expected]'s.
 */
internal fun WorkflowIdentifier.realTypeMatchesClassExpectation(
  expected: WorkflowIdentifier
): Boolean {
  val expectedType = expected.realType
  val actualType = realType
  return actualType.matchesExpectation(expectedType)
}

/**
 * Returns true iff this identifier's [WorkflowIdentifier.getRealIdentifierType]  has the same
 * class (or is a subtype) of the [expectedKClass].
 */
internal fun WorkflowIdentifier.realTypeMatchesClassExpectation(
  expectedKClass: KClass<*>
): Boolean {
  val actualType = realType
  return actualType.matchesClassExpectation(expectedKClass)
}

internal fun WorkflowIdentifierType.matchesExpectation(expected: WorkflowIdentifierType): Boolean {
  return when {
    this is Snapshottable && expected is Snapshottable -> matchesSnapshottable(expected)
    this is Unsnapshottable && expected is Unsnapshottable -> expected.kType.isSupertypeOf(kType)
    else -> false
  }
}

internal fun WorkflowIdentifierType.matchesClassExpectation(expectedKClass: KClass<*>): Boolean {
  return when (this) {
    is Snapshottable -> kClass?.let { actualKClass ->
      expectedKClass.isSuperclassOf(actualKClass) || actualKClass.isJavaMockOf(expectedKClass)
    } == true
    is Unsnapshottable -> (kType.classifier as? KClass<*>)?.let { actualKClass ->
      expectedKClass.isSuperclassOf(actualKClass) || actualKClass.isJavaMockOf(expectedKClass)
    } == true
    else -> false
  }
}

private fun Snapshottable.matchesSnapshottable(expected: Snapshottable): Boolean =
  kClass?.let { actualKClass ->
    expected.kClass?.let { expectedKClass ->
      expectedKClass.isSuperclassOf(actualKClass) || actualKClass.isJavaMockOf(expectedKClass)
    }
  } == true

/**
 * Falls back to using Java reflection to determine subclass relationship.
 *
 * Kotlin's [isSuperclassOf] doesn't play nice with Mockito or Mockk:
 * `Interface::class.isSuperclassOf(mock<Interface>()::class)` will return false.
 *
 * See https://github.com/square/workflow-kotlin/issues/155 and
 * https://youtrack.jetbrains.com/issue/KT-40863.
 */
private fun KClass<*>.isJavaMockOf(type: KClass<*>): Boolean =
  type.java.isAssignableFrom(this.java)
