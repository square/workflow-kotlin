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
import com.squareup.workflow.RenderContext
import com.squareup.workflow.Sink
import com.squareup.workflow.StatefulWorkflow
import com.squareup.workflow.Worker
import com.squareup.workflow.Workflow
import com.squareup.workflow.WorkflowAction
import com.squareup.workflow.WorkflowAction.Companion.noAction
import com.squareup.workflow.WorkflowIdentifier
import com.squareup.workflow.WorkflowOutput
import com.squareup.workflow.applyTo
import com.squareup.workflow.identifier
import com.squareup.workflow.testing.RealRenderTester.Expectation.ExpectedSideEffect
import com.squareup.workflow.testing.RealRenderTester.Expectation.ExpectedWorker
import com.squareup.workflow.testing.RealRenderTester.Expectation.ExpectedWorkflow

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
    open val output: WorkflowOutput<OutputT>? = null

    data class ExpectedWorkflow<OutputT, RenderingT>(
      val identifier: WorkflowIdentifier,
      val key: String,
      val assertProps: (props: Any?) -> Unit,
      val rendering: RenderingT,
      override val output: WorkflowOutput<OutputT>?
    ) : Expectation<OutputT>()

    data class ExpectedWorker<out OutputT>(
      val matchesWhen: (otherWorker: Worker<*>) -> Boolean,
      val key: String,
      override val output: WorkflowOutput<OutputT>?
    ) : Expectation<OutputT>()

    data class ExpectedSideEffect(val key: String) : Expectation<Nothing>()
  }

  override val actionSink: Sink<WorkflowAction<PropsT, StateT, OutputT>> get() = this

  override fun <ChildOutputT, ChildRenderingT> expectWorkflow(
    identifier: WorkflowIdentifier,
    rendering: ChildRenderingT,
    key: String,
    assertProps: (props: Any?) -> Unit,
    output: WorkflowOutput<ChildOutputT>?
  ): RenderTester<PropsT, StateT, OutputT, RenderingT> {
    val expectedWorkflow = ExpectedWorkflow(identifier, key, assertProps, rendering, output)
    if (output != null) {
      checkNoOutputs(expectedWorkflow)
      childWillEmitOutput = true
    }
    expectations += expectedWorkflow
    return this
  }

  override fun expectWorker(
    matchesWhen: (otherWorker: Worker<*>) -> Boolean,
    key: String,
    output: WorkflowOutput<Any?>?
  ): RenderTester<PropsT, StateT, OutputT, RenderingT> {
    val expectedWorker = ExpectedWorker(matchesWhen, key, output)
    if (output != null) {
      checkNoOutputs(expectedWorker)
      childWillEmitOutput = true
    }
    expectations += expectedWorker
    return this
  }

  override fun expectSideEffect(key: String): RenderTester<PropsT, StateT, OutputT, RenderingT> {
    if (expectations.any { it is ExpectedSideEffect && it.key == key }) {
      throw AssertionError("Already expecting side effect with key \"$key\".")
    }
    expectations += ExpectedSideEffect(key)
    return this
  }

  override fun render(block: (RenderingT) -> Unit): RenderTestResult<PropsT, StateT, OutputT> {
    // Clone the expectations to run a "dry" render pass.
    val noopContext = deepCloneForRender()
    workflow.render(props, state, noopContext)

    workflow.render(props, state, this)
        .also(block)

    // Ensure all expected children ran.
    if (expectations.isNotEmpty()) {
      throw AssertionError(
          "Expected ${expectations.size} more workflows, workers, or side effects to be ran:\n" +
              expectations.joinToString(separator = "\n") { "  $it" }
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
    val expected = consumeExpectedChildWorkflow<ExpectedWorkflow<*, *>>(
        predicate = { expectation ->
          expectation.identifier.matchesActualIdentifierForTest(child.identifier) &&
              expectation.key == key
        },
        description = {
          "child workflow ${child.identifier}" +
              key.takeUnless { it.isEmpty() }
                  ?.let { " with key \"$it\"" }
                  .orEmpty()
        }
    )

    expected.assertProps(props)

    if (expected.output != null) {
      check(processedAction == null)
      @Suppress("UNCHECKED_CAST")
      processedAction = handler(expected.output.value as ChildOutputT)
    }

    @Suppress("UNCHECKED_CAST")
    return expected.rendering as ChildRenderingT
  }

  override fun <T> runningWorker(
    worker: Worker<T>,
    key: String,
    handler: (T) -> WorkflowAction<PropsT, StateT, OutputT>
  ) {
    val expected = consumeExpectedWorker<ExpectedWorker<*>>(
        predicate = { it.matchesWhen(worker) && it.key == key },
        description = {
          "worker $worker" +
              key.takeUnless { it.isEmpty() }
                  ?.let { " with key \"$it\"" }
                  .orEmpty()
        }
    )

    if (expected?.output != null) {
      check(processedAction == null)
      @Suppress("UNCHECKED_CAST")
      processedAction = handler(expected.output.value as T)
    }
  }

  override fun runningSideEffect(
    key: String,
    sideEffect: suspend () -> Unit
  ) {
    consumeExpectedSideEffect(key) { "sideEffect with key \"$key\"" }
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

  private inline fun <reified T : ExpectedWorkflow<*, *>> consumeExpectedChildWorkflow(
    predicate: (T) -> Boolean,
    description: () -> String
  ): T {
    val matchedExpectations = expectations.filterIsInstance<T>()
        .filter(predicate)
    val expected = matchedExpectations.singleOrNull()
        ?: run {
          throw when {
            matchedExpectations.isEmpty() -> AssertionError(
                "Tried to render unexpected ${description()}"
            )
            else -> AssertionError(
                "Multiple workflows matched ${description()}:\n" +
                    matchedExpectations.joinToString(separator = "\n") { "  $it" }
            )
          }
        }

    // Move the workflow to the consumed list.
    expectations -= expected
    consumedExpectations += expected

    return expected
  }

  private inline fun <reified T : ExpectedWorker<*>> consumeExpectedWorker(
    predicate: (T) -> Boolean,
    description: () -> String
  ): T? {
    val matchedExpectations = expectations.filterIsInstance<T>()
        .filter(predicate)
    return when (matchedExpectations.size) {
      0 -> null
      1 -> {
        val expected = matchedExpectations[0]
        // Move the worker to the consumed list.
        expectations -= expected
        consumedExpectations += expected
        expected
      }
      else -> throw AssertionError(
          "Multiple workers matched ${description()}:\n" +
              matchedExpectations.joinToString(separator = "\n") { "  $it" }
      )
    }
  }

  private inline fun consumeExpectedSideEffect(
    key: String,
    description: () -> String
  ): ExpectedSideEffect? {
    val matchedExpectations = expectations.filterIsInstance<ExpectedSideEffect>()
        .filter { it.key == key }
    return when (matchedExpectations.size) {
      0 -> null
      1 -> {
        val expected = matchedExpectations[0]
        // Move the side effect to the consumed list.
        expectations -= expected
        consumedExpectations += expected
        expected
      }
      else -> throw AssertionError(
          "Multiple side effects matched ${description()}:\n" +
              matchedExpectations.joinToString(separator = "\n") { "  $it" }
      )
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
