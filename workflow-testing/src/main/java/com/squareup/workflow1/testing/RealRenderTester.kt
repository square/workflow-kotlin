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
import com.squareup.workflow1.RenderContext
import com.squareup.workflow1.Sink
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.Worker
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.WorkflowAction.Companion.noAction
import com.squareup.workflow1.WorkflowIdentifier
import com.squareup.workflow1.WorkflowOutput
import com.squareup.workflow1.applyTo
import com.squareup.workflow1.identifier
import com.squareup.workflow1.testing.RealRenderTester.Expectation.ExpectedSideEffect
import com.squareup.workflow1.testing.RealRenderTester.Expectation.ExpectedWorker
import com.squareup.workflow1.testing.RealRenderTester.Expectation.ExpectedWorkflow
import com.squareup.workflow1.testing.RenderTester.ChildWorkflowMatch
import com.squareup.workflow1.testing.RenderTester.ChildWorkflowMatch.Matched
import com.squareup.workflow1.testing.RenderTester.RenderChildInvocation
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.allSupertypes
import kotlin.reflect.full.isSuperclassOf
import kotlin.reflect.full.isSupertypeOf

private const val WORKFLOW_INTERFACE_NAME = "com.squareup.workflow1.Workflow"

@OptIn(ExperimentalWorkflowApi::class)
internal class RealRenderTester<PropsT, StateT, OutputT, RenderingT>(
  private val workflow: StatefulWorkflow<PropsT, StateT, OutputT, RenderingT>,
  private val props: PropsT,
  private val state: StateT,
  private val expectations: MutableList<Expectation<*>> = mutableListOf(),
  private val consumedExpectations: MutableList<Expectation<*>> = mutableListOf(),
  private var childWillEmitOutput: Boolean = false,
  private var processedAction: WorkflowAction<PropsT, StateT, OutputT>? = null
) : RenderTester<PropsT, StateT, OutputT, RenderingT>,
    RenderContext<PropsT, StateT, OutputT>,
    RenderTestResult<PropsT, StateT, OutputT>,
    Sink<WorkflowAction<PropsT, StateT, OutputT>> {

  internal sealed class Expectation<out OutputT> {
    abstract fun describe(): String

    open val output: WorkflowOutput<OutputT>? = null

    data class ExpectedWorkflow(
      val matcher: (RenderChildInvocation) -> ChildWorkflowMatch,
      val exactMatch: Boolean,
      val description: String
    ) : Expectation<Any?>() {
      override fun describe(): String = description
    }

    data class ExpectedWorker<out OutputT>(
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
  }

  override val actionSink: Sink<WorkflowAction<PropsT, StateT, OutputT>> get() = this

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

  @OptIn(ExperimentalStdlibApi::class)
  override fun render(block: (RenderingT) -> Unit): RenderTestResult<PropsT, StateT, OutputT> {
    // Allow unexpected workers.
    expectWorker(description = "unexpected worker", exactMatch = false) { _, _ -> true }

    // Clone the expectations to run a "dry" render pass.
    val noopContext = deepCloneForRender()
    workflow.render(props, state, noopContext)

    workflow.render(props, state, this)
        .also(block)

    // Ensure all exact matches were consumed.
    val unconsumedExactMatches = expectations.filter {
      when (it) {
        is ExpectedWorkflow -> it.exactMatch
        // Workers are always exact matches.
        is ExpectedWorker -> true
        is ExpectedSideEffect -> it.exactMatch
      }
    }
    if (unconsumedExactMatches.isNotEmpty()) {
      throw AssertionError(
          "Expected ${unconsumedExactMatches.size} more workflows, workers, or side effects to be ran:\n" +
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
    val description = buildString {
      append("child ")
      append(child.identifier.describeRealIdentifier() ?: "workflow ${child.identifier}")
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
            "${match.output.value} but $processedAction was already processed."
      }
      @Suppress("UNCHECKED_CAST")
      processedAction = handler(match.output.value as ChildOutputT)
    }

    @Suppress("UNCHECKED_CAST")
    return match.childRendering as ChildRenderingT
  }

  override fun runningSideEffect(
    key: String,
    sideEffect: suspend () -> Unit
  ) {
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

  override fun send(value: WorkflowAction<PropsT, StateT, OutputT>) {
    checkNoOutputs()
    check(processedAction == null) {
      "Tried to send action to sink after another action was already processed:\n" +
          "  processed action=$processedAction\n" +
          "  attempted action=$value"
    }
    processedAction = value
  }

  override fun verifyAction(block: (WorkflowAction<PropsT, StateT, OutputT>) -> Unit) {
    val action = processedAction ?: noAction()
    block(action)
  }

  override fun verifyActionResult(block: (newState: StateT, output: WorkflowOutput<OutputT>?) -> Unit) {
    verifyAction {
      val (state, output) = it.applyTo(props, state)
      block(state, output)
    }
  }

  private fun deepCloneForRender(): RenderContext<PropsT, StateT, OutputT> = RealRenderTester(
      workflow, props, state,
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
@OptIn(ExperimentalWorkflowApi::class)
internal fun WorkflowIdentifier.realTypeMatchesExpectation(
  expected: WorkflowIdentifier
): Boolean {
  val expectedType = expected.getRealIdentifierType()
  val actualType = getRealIdentifierType()
  return when {
    expectedType is KType && actualType is KType -> {
      expectedType.isSupertypeOf(actualType)
    }
    expectedType is KClass<*> && actualType is KClass<*> -> {
      expectedType.isSuperclassOf(actualType)
    }
    else -> {
      error(
          "Expected WorkflowIdentifier type to be KType or KClass: " +
              "actual: $actualType, expected: $expectedType"
      )
    }
  }
}
