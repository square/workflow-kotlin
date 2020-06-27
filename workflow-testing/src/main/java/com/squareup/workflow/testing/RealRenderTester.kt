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

import com.squareup.workflow.RenderContext
import com.squareup.workflow.Sink
import com.squareup.workflow.StatefulWorkflow
import com.squareup.workflow.Worker
import com.squareup.workflow.Workflow
import com.squareup.workflow.WorkflowAction
import com.squareup.workflow.WorkflowAction.Companion.noAction
import com.squareup.workflow.applyTo
import com.squareup.workflow.testing.RealRenderTester.Expectation.ExpectedSideEffect
import com.squareup.workflow.testing.RealRenderTester.Expectation.ExpectedWorker
import com.squareup.workflow.testing.RealRenderTester.Expectation.ExpectedWorkflow
import kotlin.reflect.KClass

internal class RealRenderTester<PropsT, StateT, OutputT, RenderingT>(
  private val workflow: StatefulWorkflow<PropsT, StateT, OutputT, RenderingT>,
  private val props: PropsT,
  private val state: StateT,
  private val expectations: MutableList<Expectation<*>> = mutableListOf(),
  private val consumedExpectations: MutableList<Expectation<*>> = mutableListOf(),
  private var childWillEmitOutput: Boolean = false,
  private var processedAction: WorkflowAction<StateT, OutputT>? = null
) : RenderTester<PropsT, StateT, OutputT, RenderingT>,
    RenderContext<StateT, OutputT>,
    RenderTestResult<StateT, OutputT>,
    Sink<WorkflowAction<StateT, OutputT>> {

  internal sealed class Expectation<out OutputT> {
    open val output: EmittedOutput<OutputT>? = null

    data class ExpectedWorkflow<OutputT, RenderingT>(
      val workflowType: KClass<out Workflow<*, OutputT, RenderingT>>,
      val key: String,
      val assertProps: (props: Any?) -> Unit,
      val rendering: RenderingT,
      override val output: EmittedOutput<OutputT>?
    ) : Expectation<OutputT>()

    data class ExpectedWorker<out OutputT>(
      val matchesWhen: (otherWorker: Worker<*>) -> Boolean,
      val key: String,
      override val output: EmittedOutput<OutputT>?
    ) : Expectation<OutputT>()

    data class ExpectedSideEffect(val key: String) : Expectation<Nothing>()
  }

  override val actionSink: Sink<WorkflowAction<StateT, OutputT>> get() = this

  override fun <ChildPropsT, ChildOutputT, ChildRenderingT> expectWorkflow(
    workflowType: KClass<out Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>>,
    rendering: ChildRenderingT,
    key: String,
    assertProps: (props: ChildPropsT) -> Unit,
    output: EmittedOutput<ChildOutputT>?
  ): RenderTester<PropsT, StateT, OutputT, RenderingT> {
    @Suppress("UNCHECKED_CAST")
    val assertAnyProps = { props: Any? -> assertProps(props as ChildPropsT) }
    val expectedWorkflow = ExpectedWorkflow(workflowType, key, assertAnyProps, rendering, output)
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
    output: EmittedOutput<Any?>?
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

  override fun render(block: (RenderingT) -> Unit): RenderTestResult<StateT, OutputT> {
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
    handler: (ChildOutputT) -> WorkflowAction<StateT, OutputT>
  ): ChildRenderingT {
    val expected = consumeExpectedChildWorkflow<ExpectedWorkflow<*, *>>(
        predicate = { it.workflowType.isInstance(child) && it.key == key },
        description = {
          "child workflow ${child::class.java.name}" +
              key.takeUnless { it.isEmpty() }
                  ?.let { " with key \"$it\"" }
                  .orEmpty()
        }
    )

    expected.assertProps(props)

    if (expected.output != null) {
      check(processedAction == null)
      @Suppress("UNCHECKED_CAST")
      processedAction = handler(expected.output.output as ChildOutputT)
    }

    @Suppress("UNCHECKED_CAST")
    return expected.rendering as ChildRenderingT
  }

  override fun <T> runningWorker(
    worker: Worker<T>,
    key: String,
    handler: (T) -> WorkflowAction<StateT, OutputT>
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
      processedAction = handler(expected.output.output as T)
    }
  }

  override fun runningSideEffect(
    key: String,
    sideEffect: suspend () -> Unit
  ) {
    consumeExpectedSideEffect(key) { "sideEffect with key \"$key\"" }
  }

  override fun send(value: WorkflowAction<StateT, OutputT>) {
    checkNoOutputs()
    check(processedAction == null) {
      "Tried to send action to sink after another action was already processed:\n" +
          "  processed action=$processedAction\n" +
          "  attempted action=$value"
    }
    processedAction = value
  }

  @Suppress("OverridingDeprecatedMember")
  override fun <EventT : Any> onEvent(
    handler: (EventT) -> WorkflowAction<StateT, OutputT>
  ): (EventT) -> Unit = { event -> send(handler(event)) }

  override fun verifyAction(block: (WorkflowAction<StateT, OutputT>) -> Unit) {
    val action = processedAction ?: noAction()
    block(action)
  }

  override fun verifyActionState(block: (newState: StateT) -> Unit) = apply {
    verifyAction { action ->
      // Don't care about output.
      val (newState, _) = action.applyTo(state, mapOutput = {})
      block(newState)
    }
  }

  override fun verifyActionOutput(block: (output: OutputT) -> Unit) = apply {
    verifyAction { action ->
      var outputWasSet = false
      action.applyTo(state) { output ->
        outputWasSet = true
        block(output)
      }
      if (!outputWasSet) {
        throw AssertionError("Expected action to set an output")
      }
    }
  }

  override fun verifyNoActionOutput() = apply {
    verifyAction { action ->
      action.applyTo(state) {
        throw AssertionError("Expected no output, but action set output to: $it")
      }
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
                "Tried to render unexpected ${description()}."
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

  private fun deepCloneForRender(): RenderContext<StateT, OutputT> = RealRenderTester(
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
