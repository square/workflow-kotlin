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
import com.squareup.workflow1.ImpostorWorkflow
import com.squareup.workflow1.Sink
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.StatelessWorkflow
import com.squareup.workflow1.Worker
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.WorkflowAction.Companion.noAction
import com.squareup.workflow1.WorkflowAction.Updater
import com.squareup.workflow1.WorkflowIdentifier
import com.squareup.workflow1.WorkflowOutput
import com.squareup.workflow1.contraMap
import com.squareup.workflow1.identifier
import com.squareup.workflow1.renderChild
import com.squareup.workflow1.rendering
import com.squareup.workflow1.runningWorker
import com.squareup.workflow1.stateful
import com.squareup.workflow1.stateless
import com.squareup.workflow1.testing.RenderTester.ChildWorkflowMatch.Matched
import com.squareup.workflow1.unsnapshottableIdentifier
import com.squareup.workflow1.workflowIdentifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

@OptIn(ExperimentalWorkflowApi::class, ExperimentalStdlibApi::class)
class RealRenderTesterTest {

  private interface OutputWhateverChild : Workflow<Unit, Unit, Unit>
  private interface OutputNothingChild : Workflow<Unit, Nothing, Unit>

  @Test fun `renderChild throws when already expecting workflow output`() {
    val child1 = Workflow.stateless<Unit, Unit, Unit> {}
    val child2 = Workflow.stateless<Unit, Unit, Unit> {}
    val workflow = Workflow.stateless<Unit, Unit, Unit> {
      renderChild(child1) { noAction() }
      renderChild(child2) { noAction() }
    }
    val tester = workflow.testRender(Unit)
        .expectWorkflow(child1.identifier, rendering = Unit, output = WorkflowOutput(Unit))
        .expectWorkflow(child2.identifier, rendering = Unit, output = WorkflowOutput(Unit))

    val failure = assertFailsWith<IllegalStateException> {
      tester.render()
    }
    assertEquals(
        "Expected only one output to be expected: child ${child2.identifier} " +
            "expected to emit kotlin.Unit but WorkflowAction.noAction() was already processed.",
        failure.message
    )
  }

  @Test fun `runningWorker throws when already expecting workflow output`() {
    val child = Workflow.stateless<Unit, Unit, Unit> {}
    val worker = Worker.finished<Unit>()
    val workflow = Workflow.stateless<Unit, Unit, Unit> {
      renderChild(child) { noAction() }
      runningWorker(worker) { noAction() }
    }
    val tester = workflow.testRender(Unit)
        .expectWorkflow(child.identifier, rendering = Unit, output = WorkflowOutput(Unit))
        .expectWorker(typeOf<Worker<Unit>>(), output = WorkflowOutput(Unit))

    val failure = assertFailsWith<IllegalStateException> {
      tester.render()
    }

    assertEquals(
        "Expected only one output to be expected: child worker ${typeOf<Worker<Unit>>()} expected to emit " +
            "kotlin.Unit but WorkflowAction.noAction() was already processed.",
        failure.message
    )
  }

  @Test fun `expectWorkflow without output doesn't throw when already expecting output`() {
    // Don't need an implementation, the test should fail before even calling render.
    val workflow = Workflow.stateless<Unit, Unit, Unit> {}
    val tester = workflow.testRender(Unit)
        .expectWorkflow(
            OutputWhateverChild::class, rendering = Unit,
            output = WorkflowOutput(Unit)
        )

    // Doesn't throw.
    tester.expectWorkflow(workflow::class, rendering = Unit)
  }

  @Test fun `expectSideEffect throws when already expecting side effect with same key`() {
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      runningSideEffect("the key") {}
    }
    val tester = workflow.testRender(Unit)
        .expectSideEffect(key = "the key")
        .expectSideEffect(description = "duplicate match") { it == "the key" }

    val error = assertFailsWith<AssertionError> {
      tester.render()
    }
    assertEquals(
        "Multiple expectations matched side effect with key \"the key\":\n" +
            "  side effect with key \"the key\"\n" +
            "  duplicate match",
        error.message
    )
  }

  @Test fun `sideEffect matches on key`() {
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      runningSideEffect("the key") {}
    }

    workflow.testRender(Unit)
        .expectSideEffect("the key")
        .render {}
  }

  @Test fun `expectSideEffect doesn't match key`() {
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {}
    val tester = workflow.testRender(Unit)
        .expectSideEffect("the key")

    val error = assertFailsWith<AssertionError> {
      tester.render {}
    }
    assertEquals(
        """
          Expected 1 more workflows, workers, or side effects to be ran:
            side effect with key "the key"
        """.trimIndent(),
        error.message
    )
  }

  @Test fun `sending to sink throws when called multiple times`() {
    class TestAction(private val name: String) : WorkflowAction<Unit, Unit, Nothing> {
      override fun Updater<Unit, Unit, Nothing>.apply() {}
      override fun toString(): String = "TestAction($name)"
    }

    val workflow = Workflow.stateful<Unit, Nothing, Sink<TestAction>>(
        initialState = Unit,
        render = { actionSink.contraMap { it } }
    )
    val action1 = TestAction("1")
    val action2 = TestAction("2")

    workflow.testRender(Unit)
        .render { sink ->
          sink.send(action1)

          val error = assertFailsWith<IllegalStateException> {
            sink.send(action2)
          }
          assertEquals(
              "Tried to send action to sink after another action was already processed:\n" +
                  "  processed action=$action1\n" +
                  "  attempted action=$action2",
              error.message
          )
        }
  }

  @Test fun `sending to sink throws when child output expected`() {
    class TestAction : WorkflowAction<Unit, Unit, Nothing> {
      override fun Updater<Unit, Unit, Nothing>.apply() {}
      override fun toString(): String = "TestAction"
    }

    val workflow = Workflow.stateful<Unit, Nothing, Sink<TestAction>>(
        initialState = Unit,
        render = {
          // Need to satisfy the expectation.
          runningWorker(Worker.finished<Unit>()) { noAction() }
          return@stateful actionSink.contraMap { it }
        }
    )

    workflow.testRender(Unit)
        .expectWorker(typeOf<Worker<Unit>>(), output = WorkflowOutput(Unit))
        .render { sink ->
          val error = assertFailsWith<IllegalStateException> {
            sink.send(TestAction())
          }
          assertEquals(
              "Tried to send action to sink after another action was already processed:\n" +
                  "  processed action=WorkflowAction.noAction()\n" +
                  "  attempted action=TestAction",
              error.message
          )
        }
  }

  @Test fun `failures from render block escape`() {
    val workflow = Workflow.stateless<Unit, Nothing, Unit> { }
    val tester = workflow.testRender(Unit)

    val error = assertFailsWith<AssertionError> {
      tester.render {
        throw AssertionError("expected failure")
      }
    }
    assertEquals("expected failure", error.message)
  }

  @Test fun `renderChild throws when none expected`() {
    val child = Workflow.stateless<Unit, Nothing, Unit> { }
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      renderChild(child)
    }
    val tester = workflow.testRender(Unit)

    val error = assertFailsWith<AssertionError> {
      tester.render()
    }
    assertEquals(
        "Tried to render unexpected child ${child.identifier}",
        error.message
    )
  }

  @Test fun `multiple matching inexact workflow matches are allowed`() {
    val child = Workflow.stateless<Unit, Nothing, String> { fail() }
    val workflow = Workflow.stateless<Unit, Nothing, String> {
      renderChild(child)
    }

    workflow.testRender(Unit)
        .expectWorkflow(exactMatch = false, description = "expect1") { Matched("one") }
        .expectWorkflow(exactMatch = false, description = "expect2") { Matched("two") }
        .render { rendering ->
          assertEquals("one", rendering)
        }
  }

  @Test fun `matching inexact workflow matches are allowed with matching exact match`() {
    val child = Workflow.stateless<Unit, Nothing, String> { fail() }
    val workflow = Workflow.stateless<Unit, Nothing, String> {
      renderChild(child)
    }

    workflow.testRender(Unit)
        .expectWorkflow(exactMatch = false, description = "expect1") { Matched("one") }
        .expectWorkflow(exactMatch = true, description = "expect2") { Matched("two") }
        .render { rendering ->
          assertEquals("two", rendering)
        }
  }

  @Test fun `unmatching inexact workflow matches are allowed`() {
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {}

    workflow.testRender(Unit)
        .expectWorkflow(exactMatch = false, description = "expect1") { Matched(Unit) }
        .expectWorkflow(exactMatch = false, description = "expect2") { Matched(Unit) }
        .render()
  }

  @Test fun `multiple matching inexact side effect matches are allowed`() {
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      runningSideEffect("side effect") {}
    }

    workflow.testRender(Unit)
        .expectSideEffect(exactMatch = false, description = "expect1") { true }
        .expectSideEffect(exactMatch = false, description = "expect2") { true }
        .render()
  }

  @Test fun `matching inexact side effect matches are allowed with matching exact match`() {
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      runningSideEffect("side effect") {}
    }

    workflow.testRender(Unit)
        .expectSideEffect(exactMatch = false, description = "expect1") { true }
        .expectSideEffect(exactMatch = true, description = "expect2") { true }
        .render()
  }

  @Test fun `non-matching inexact side effect is ignored`() {
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {}

    workflow.testRender(Unit)
        .expectSideEffect(exactMatch = false, description = "expect1") { false }
        .render()
  }

  @Test fun `distinct side effect expectations match individual side effects`() {
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      runningSideEffect("effect1") {}
      runningSideEffect("effect2") {}
    }

    workflow.testRender(Unit)
        .expectSideEffect(exactMatch = true, description = "expect2") { it == "effect2" }
        .expectSideEffect(exactMatch = true, description = "expect1") { it == "effect1" }
        .render()
  }

  @Test fun `non-matched inexact side effect matches are allowed`() {
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {}

    workflow.testRender(Unit)
        .expectSideEffect(exactMatch = false, description = "expect1") { true }
        .expectSideEffect(exactMatch = false, description = "expect2") { true }
        .render()
  }

  @Test fun `runningSideEffect throws when no expectations match`() {
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      runningSideEffect("effect") {}
    }
    val tester = workflow.testRender(Unit)

    val error = assertFailsWith<AssertionError> {
      tester.render()
    }
    assertEquals("Tried to run unexpected side effect with key \"effect\"", error.message)
  }

  @Test fun `runningSideEffect throws when no expectations match but other side effects matched`() {
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      runningSideEffect("expected") {}
      runningSideEffect("unexpected") {}
    }
    val tester = workflow.testRender(Unit)
        .expectSideEffect("expected")

    val error = assertFailsWith<AssertionError> {
      tester.render()
    }
    assertEquals("Tried to run unexpected side effect with key \"unexpected\"", error.message)
  }

  @Test fun `runningSideEffect throws when multiple expectations match`() {
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      runningSideEffect("effect") {}
    }
    val tester = workflow.testRender(Unit)
        .expectSideEffect("effect")
        .expectSideEffect(description = "custom", exactMatch = true) { key -> "effect" in key }

    val error = assertFailsWith<AssertionError> {
      tester.render()
    }
    assertEquals(
        "Multiple expectations matched side effect with key \"effect\":\n" +
            "  side effect with key \"effect\"\n" +
            "  custom",
        error.message
    )
  }

  @Test fun `runningSideEffect throws on duplicate call`() {
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      runningSideEffect("key") {}
      runningSideEffect("key") {}
    }

    val tester = workflow.testRender(Unit)
        .expectSideEffect("key")

    val error = assertFailsWith<IllegalArgumentException> {
      tester.render()
    }
    assertEquals("Expected side effect keys to be unique: \"key\"", error.message)
  }

  @Test
  fun `renderChild rendering non-Unit throws when none expected and unexpected children are allowed`() {
    val child = Workflow.stateless<Unit, Nothing, Int> { 42 }
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      renderChild(child)
    }
    val tester = workflow.testRender(Unit)

    val error = assertFailsWith<AssertionError> {
      tester.render()
    }
    assertEquals(
        "Tried to render unexpected child ${child.identifier}",
        error.message
    )
  }

  @Test
  fun `renderChild rendering Unit throws when no expectations match`() {
    val child = Workflow.stateless<Unit, Nothing, Unit> { }
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      renderChild(child)
    }
    val tester = workflow.testRender(Unit)
        .expectWorkflow(OutputNothingChild::class, rendering = Unit)

    val error = assertFailsWith<AssertionError> {
      tester.render()
    }
    assertEquals(
        "Tried to render unexpected child ${child.identifier}",
        error.message
    )
  }

  @Test
  fun `renderChild rendering non-Unit throws when no expectations match`() {
    val child = Workflow.stateless<Unit, Nothing, Int> { 42 }
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      renderChild(child)
    }
    val tester = workflow.testRender(Unit)
        .expectWorkflow(OutputNothingChild::class, rendering = Unit)

    val error = assertFailsWith<AssertionError> {
      tester.render()
    }
    assertEquals(
        "Tried to render unexpected child ${child.identifier}",
        error.message
    )
  }

  @Test fun `renderChild with key throws when none expected`() {
    val child = Workflow.stateless<Unit, Nothing, Unit> { }
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      renderChild(child, key = "key")
    }
    val tester = workflow.testRender(Unit)

    val error = assertFailsWith<AssertionError> {
      tester.render()
    }
    assertEquals(
        "Tried to render unexpected child ${child.identifier} with key \"key\"",
        error.message
    )
  }

  @Test fun `renderChild with key throws when no expectations match`() {
    val child = Workflow.stateless<Unit, Nothing, Unit> { }
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      renderChild(child, key = "key")
    }
    val tester = workflow.testRender(Unit)
        .expectWorkflow(OutputNothingChild::class, rendering = Unit)

    val error = assertFailsWith<AssertionError> {
      tester.render()
    }
    assertEquals(
        "Tried to render unexpected child ${child.identifier} with key \"key\"",
        error.message
    )
  }

  @Test
  fun `renderChild with key throws when key doesn't match`() {
    val child = Workflow.stateless<Unit, Nothing, Unit> { }
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      renderChild(child, key = "key")
    }
    val tester = workflow.testRender(Unit)
        .expectWorkflow(OutputNothingChild::class, rendering = Unit, key = "wrong key")

    val error = assertFailsWith<AssertionError> {
      tester.render()
    }
    assertEquals(
        "Tried to render unexpected child ${child.identifier} with key \"key\"",
        error.message
    )
  }

  @Test fun `renderChild throws when multiple expectations match`() {
    class Child : OutputNothingChild, StatelessWorkflow<Unit, Nothing, Unit>() {
      override fun render(
        props: Unit,
        context: RenderContext
      ) {
        // Nothing to do.
      }
    }

    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      renderChild(Child())
    }
    val tester = workflow.testRender(Unit)
        .expectWorkflow(OutputNothingChild::class, rendering = Unit)
        .expectWorkflow(Child::class, rendering = Unit)

    val error = assertFailsWith<AssertionError> {
      tester.render()
    }
    assertEquals(
        """
          Multiple expectations matched child ${Child::class.workflowIdentifier}:
            workflow identifier=${OutputNothingChild::class.workflowIdentifier}, key=, rendering=kotlin.Unit, output=null
            workflow identifier=${Child::class.workflowIdentifier}, key=, rendering=kotlin.Unit, output=null
        """.trimIndent(),
        error.message
    )
  }

  @Test fun `renderChild throws on duplicate call`() {
    val child = Workflow.rendering(Unit)
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      renderChild(child)
      renderChild(child)
    }

    val tester = workflow.testRender(Unit)
        .expectWorkflow<Nothing, Unit>(child.identifier, Unit)

    val error = assertFailsWith<IllegalArgumentException> {
      tester.render()
    }
    assertEquals("Expected keys to be unique for ${child.identifier}: key=\"\"", error.message)
  }

  @Test fun `runningWorker doesn't throw when none expected`() {
    val worker = object : Worker<Nothing> {
      override fun doesSameWorkAs(otherWorker: Worker<*>): Boolean = true
      override fun run(): Flow<Nothing> = emptyFlow()
      override fun toString(): String = "TestWorker"
    }

    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      runningWorker(worker)
    }
    val tester = workflow.testRender(Unit)
    tester.render()
  }

  @Test fun `render throws when worker expectation doesn't match`() {
    val worker = object : Worker<String> {
      override fun doesSameWorkAs(otherWorker: Worker<*>): Boolean = true
      override fun run(): Flow<Nothing> = emptyFlow()
      override fun toString(): String = "TestWorker"
    }

    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      runningWorker(worker) { fail() }
    }
    val tester = workflow.testRender(Unit)
        .expectWorker(typeOf<Worker<Unit>>())

    val error = assertFailsWith<AssertionError> {
      tester.render()
    }

    assertTrue(
        error.message!!.startsWith("Expected 1 more workflows, workers, or side effects to be ran:")
    )
  }

  @Test fun `runningWorker with key does not throw when none expected`() {
    val worker = object : Worker<Nothing> {
      override fun doesSameWorkAs(otherWorker: Worker<*>): Boolean = true
      override fun run(): Flow<Nothing> = emptyFlow()
      override fun toString(): String = "TestWorker"
    }

    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      runningWorker(worker, key = "key")
    }
    val tester = workflow.testRender(Unit)

    tester.render()
  }

  @Test fun `runningWorker with key throws when no key expected`() {
    val worker = object : Worker<Nothing> {
      override fun doesSameWorkAs(otherWorker: Worker<*>): Boolean = true
      override fun run(): Flow<Nothing> = emptyFlow()
      override fun toString(): String = "TestWorker"
    }

    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      runningWorker(worker, key = "key")
    }
    val tester = workflow.testRender(Unit)
        .expectWorker(typeOf<Worker<Unit>>())

    val error = assertFailsWith<AssertionError> {
      tester.render()
    }
    assertTrue(
        error.message!!.startsWith("Expected 1 more workflows, workers, or side effects to be ran:")
    )
  }

  @Test fun `runningWorker with key throws when wrong key expected`() {
    val worker = object : Worker<Nothing> {
      override fun doesSameWorkAs(otherWorker: Worker<*>): Boolean = true
      override fun run(): Flow<Nothing> = emptyFlow()
      override fun toString(): String = "TestWorker"
    }

    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      runningWorker(worker, key = "key")
    }
    val tester = workflow.testRender(Unit)
        .expectWorker(
            typeOf<Worker<Unit>>(),
            key = "wrong key"
        )

    val error = assertFailsWith<AssertionError> {
      tester.render()
    }
    assertTrue(
        error.message!!.startsWith("Expected 1 more workflows, workers, or side effects to be ran:")
    )
  }

  @Test fun `runningWorker throws when multiple expectations match`() {
    val worker = object : Worker<Nothing> {
      override fun doesSameWorkAs(otherWorker: Worker<*>): Boolean = true
      override fun run(): Flow<Nothing> = emptyFlow()
      override fun toString(): String = "TestWorker"
    }
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      runningWorker(worker)
    }
    val tester = workflow.testRender(Unit)
        .expectWorker(worker)
        .expectWorker(worker, description = "duplicate expectation")

    val error = assertFailsWith<AssertionError> {
      tester.render()
    }
    assertEquals(
        """
          Multiple expectations matched child worker com.squareup.workflow1.Worker<java.lang.Void>:
            worker TestWorker
            duplicate expectation
        """.trimIndent(),
        error.message
    )
  }

  @Test fun `runningWorker throws on duplicate call`() {
    val worker = Worker.createSideEffect {}
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      runningWorker(worker)
      runningWorker(worker)
    }

    val tester = workflow.testRender(Unit)
    val error = assertFailsWith<IllegalArgumentException> {
      tester.render()
    }
    assertEquals(
        "Expected keys to be unique for worker com.squareup.workflow1.Worker<java.lang.Void>: key=\"\"",
        error.message
    )
  }

  @Test fun `render throws when unconsumed workflow`() {
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      // Do nothing.
    }
    val tester = workflow.testRender(Unit)
        .expectWorkflow(OutputNothingChild::class, rendering = Unit)

    val error = assertFailsWith<AssertionError> {
      tester.render()
    }
    assertTrue(
        error.message!!.startsWith("Expected 1 more workflows, workers, or side effects to be ran:")
    )
  }

  @Test fun `render throws when unconsumed side effect`() {
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      // Do nothing.
    }
    val tester = workflow.testRender(Unit)
        .expectSideEffect("expectation", exactMatch = true) { true }

    val error = assertFailsWith<AssertionError> {
      tester.render()
    }
    assertTrue(
        error.message!!.startsWith("Expected 1 more workflows, workers, or side effects to be ran:")
    )
  }

  @Test fun `render throws when unconsumed worker`() {
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      // Do nothing.
    }
    val tester = workflow.testRender(Unit)
        .expectWorker(typeOf<Worker<Unit>>())

    val error = assertFailsWith<AssertionError> {
      tester.render()
    }
    assertTrue(
        error.message!!.startsWith("Expected 1 more workflows, workers, or side effects to be ran:")
    )
  }

  @Test fun `expectWorkflow matches on workflow supertype`() {
    val child = object : OutputNothingChild, StatelessWorkflow<Unit, Nothing, Unit>() {
      override fun render(
        props: Unit,
        context: RenderContext
      ) {
        // Do nothing.
      }
    }
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      renderChild(child)
    }
    val tester = workflow.testRender(Unit)
        .expectWorkflow(OutputNothingChild::class, rendering = Unit)

    tester.render()
  }

  @Test fun `expectWorkflow matches same ImpostorWorkflow class with same proxy identifiers`() {
    class TestWorkflow : Workflow<Unit, Nothing, Unit> {
      override fun asStatefulWorkflow(): StatefulWorkflow<Unit, *, Nothing, Unit> =
        throw NotImplementedError()
    }

    class TestImpostor(val proxy: Workflow<*, *, *>) : Workflow<Unit, Nothing, Unit>,
        ImpostorWorkflow {
      override val realIdentifier: WorkflowIdentifier get() = proxy.identifier
      override fun asStatefulWorkflow(): StatefulWorkflow<Unit, *, Nothing, Unit> =
        throw NotImplementedError()
    }

    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      renderChild(TestImpostor(TestWorkflow()))
    }
    workflow.testRender(Unit)
        .expectWorkflow<Nothing, Unit>(
            TestImpostor(TestWorkflow()).identifier,
            Unit
        )
        .render {}
  }

  @Test
  fun `expectWorkflow doesn't match same ImpostorWorkflow class with different proxy identifiers`() {
    class TestWorkflowActual : Workflow<Unit, Nothing, Unit> {
      override fun asStatefulWorkflow(): StatefulWorkflow<Unit, *, Nothing, Unit> =
        throw NotImplementedError()
    }

    class TestWorkflowExpected : Workflow<Unit, Nothing, Unit> {
      override fun asStatefulWorkflow(): StatefulWorkflow<Unit, *, Nothing, Unit> =
        throw NotImplementedError()
    }

    class TestImpostor(val proxy: Workflow<*, *, *>) : Workflow<Unit, Nothing, Unit>,
        ImpostorWorkflow {
      override val realIdentifier: WorkflowIdentifier get() = proxy.identifier
      override fun asStatefulWorkflow(): StatefulWorkflow<Unit, *, Nothing, Unit> =
        throw NotImplementedError()
    }

    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      renderChild(TestImpostor(TestWorkflowActual()))
    }
    val expectedId = TestImpostor(TestWorkflowExpected()).identifier
    val actualId = TestImpostor(TestWorkflowActual()).identifier

    val tester = workflow.testRender(Unit)
        .expectWorkflow<Nothing, Unit>(expectedId, Unit)

    val error = assertFailsWith<AssertionError> {
      tester.render {}
    }
    assertEquals(
        "Tried to render unexpected child $actualId", error.message
    )
  }

  @Test
  fun `expectWorkflow matches different ImpostorWorkflow classes with same proxy identifiers`() {
    class TestWorkflow : Workflow<Unit, Nothing, Unit> {
      override fun asStatefulWorkflow(): StatefulWorkflow<Unit, *, Nothing, Unit> =
        throw NotImplementedError()
    }

    class TestImpostorActual(val proxy: Workflow<*, *, *>) : Workflow<Unit, Nothing, Unit>,
        ImpostorWorkflow {
      override val realIdentifier: WorkflowIdentifier get() = proxy.identifier
      override fun asStatefulWorkflow(): StatefulWorkflow<Unit, *, Nothing, Unit> =
        throw NotImplementedError()
    }

    class TestImpostorExpected(val proxy: Workflow<*, *, *>) : Workflow<Unit, Nothing, Unit>,
        ImpostorWorkflow {
      override val realIdentifier: WorkflowIdentifier get() = proxy.identifier
      override fun asStatefulWorkflow(): StatefulWorkflow<Unit, *, Nothing, Unit> =
        throw NotImplementedError()
    }

    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      renderChild(TestImpostorActual(TestWorkflow()))
    }
    val expectedId = TestImpostorExpected(TestWorkflow()).identifier

    workflow.testRender(Unit)
        .expectWorkflow<Nothing, Unit>(expectedId, Unit)
        .render {}
  }

  @Test fun `assertProps failure fails test`() {
    val child = Workflow.stateless<String, Nothing, Unit> {}
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      renderChild(child, "wrong props")
    }
    val tester = workflow.testRender(Unit)
        .expectWorkflow(
            workflowType = child::class,
            rendering = Unit,
            assertProps = { props -> throw AssertionError("bad props: $props") }
        )

    val error = assertFailsWith<AssertionError> {
      tester.render()
    }
    assertEquals("bad props: wrong props", error.message)
  }

  private class TestAction(val name: String) : WorkflowAction<Unit, Nothing, Nothing> {
    override fun Updater<Unit, Nothing, Nothing>.apply() {}
    override fun toString(): String = "TestAction($name)"
  }

  @Test fun `verifyAction failure fails test`() {
    val workflow = Workflow.stateless<Unit, Nothing, Sink<TestAction>> {
      actionSink.contraMap { it }
    }
    val testResult = workflow.testRender(Unit)
        .render { it.send(TestAction("noop")) }

    val error = assertFailsWith<AssertionError> {
      testResult.verifyAction { throw AssertionError("action failed") }
    }
    assertEquals("action failed", error.message)
  }

  @Test fun `verifyAction verifies workflow output`() {
    val child = Workflow.stateless<Unit, String, Unit> {}
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      renderChild(child) { TestAction(it) }
    }
    val testResult = workflow.testRender(Unit)
        .expectWorkflow(
            workflowType = child::class,
            rendering = Unit,
            output = WorkflowOutput("output")
        )
        .render()

    testResult.verifyAction {
      assertTrue(it is TestAction)
      assertEquals("output", it.name)
    }
  }

  @Test fun `verifyAction verifies worker output`() {
    val worker = Worker.finished<String>()
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      runningWorker(worker) { TestAction(it) }
    }
    val testResult = workflow.testRender(Unit)
        .expectWorker(worker, output = WorkflowOutput("output"))
        .render()

    testResult.verifyAction {
      assertTrue(it is TestAction)
      assertEquals("output", it.name)
    }
  }

  @Test fun `verifyAction verifies sink send`() {
    val workflow = Workflow.stateless<Unit, Nothing, Sink<TestAction>> {
      actionSink.contraMap { it }
    }
    val testResult = workflow.testRender(Unit)
        .render { sink ->
          sink.send(TestAction("event"))
        }

    testResult.verifyAction {
      assertTrue(it is TestAction)
      assertEquals("event", it.name)
    }
  }

  @Test fun `verifyAction allows no action`() {
    val workflow = Workflow.stateless<Unit, Nothing, Sink<TestAction>> {
      actionSink.contraMap { it }
    }
    val testResult = workflow.testRender(Unit)
        .render {
          // Don't send to sink!
        }

    testResult.verifyAction { assertEquals(noAction(), it) }
    testResult.verifyActionResult { newState, output ->
      assertSame(Unit, newState)
      assertNull(output)
    }
  }

  @Test fun `verifyActionResult allows no action`() {
    val workflow = Workflow.stateless<Unit, Nothing, Sink<TestAction>> {
      actionSink.contraMap { it }
    }
    val testResult = workflow.testRender(Unit)
        .render {
          // Don't send to sink!
        }

    testResult.verifyActionResult { newState, output ->
      assertSame(Unit, newState)
      assertNull(output)
    }
  }

  @Test fun `verifyActionResult handles new state and output`() {
    class TestAction : WorkflowAction<Unit, String, String> {
      override fun Updater<Unit, String, String>.apply() {
        state = "new state"
        setOutput("output")
      }
    }

    val workflow = Workflow.stateful<Unit, String, String, Sink<TestAction>>(
        initialState = { "initial" },
        render = { _, _ -> actionSink.contraMap { it } }
    )
    val testResult = workflow.testRender(Unit)
        .render { sink ->
          sink.send(TestAction())
        }

    testResult.verifyActionResult { state, output ->
      assertEquals("new state", state)
      assertEquals("output", output?.value)
    }
  }

  @Test fun `render is executed multiple times`() {
    var renderCount = 0
    val workflow = Workflow.stateless<Unit, Nothing, Unit> { renderCount++ }

    workflow.testRender(Unit)
        .render()

    assertEquals(2, renderCount)
  }

  @Test fun `createRenderChildInvocation() for Workflow-stateless{}`() {
    val workflow = Workflow.stateless<String, Int, Unit> {}
    val invocation = createRenderChildInvocation(workflow, "props", "key")

    assertSame(workflow, invocation.workflow)
    assertEquals("props", invocation.props)
    // Broken due to https://youtrack.jetbrains.com/issue/KT-17103
    // assertEquals(typeOf<Int>(), invocation.outputType.type)
    // assertEquals(typeOf<Unit>(), invocation.renderingType.type)
    assertEquals("key", invocation.renderKey)
  }

  @Test fun `createRenderChildInvocation() for Workflow-stateful{}`() {
    val workflow = Workflow.stateful<String, Int, Unit>(
        initialState = "",
        render = {}
    )
    val invocation = createRenderChildInvocation(workflow, "props", "key")

    assertSame(workflow, invocation.workflow)
    assertEquals("props", invocation.props)
    // Broken due to https://youtrack.jetbrains.com/issue/KT-17103
    // assertEquals(typeOf<Int>(), invocation.outputType.type)
    // assertEquals(typeOf<Unit>(), invocation.renderingType.type)
    assertEquals("key", invocation.renderKey)
  }

  @Test fun `createRenderChildInvocation() for anonymous skeleton Workflow`() {
    val workflow = object : Workflow<String, Int, Unit> {
      override fun asStatefulWorkflow(): StatefulWorkflow<String, *, Int, Unit> =
        throw NotImplementedError()
    }
    val invocation = createRenderChildInvocation(workflow, "props", "key")

    assertSame(workflow, invocation.workflow)
    assertEquals("props", invocation.props)
    assertEquals(typeOf<Int>(), invocation.outputType.type)
    assertEquals(typeOf<Unit>(), invocation.renderingType.type)
    assertEquals("key", invocation.renderKey)
  }

  @Test fun `createRenderChildInvocation() for anonymous StatefulWorkflow`() {
    val workflow = object : StatefulWorkflow<String, Double, Int, Unit>() {
      override fun initialState(
        props: String,
        snapshot: Snapshot?
      ): Double = throw NotImplementedError()

      override fun render(
        props: String,
        state: Double,
        context: RenderContext
      ) = throw NotImplementedError()

      override fun snapshotState(state: Double): Snapshot? = throw NotImplementedError()
    }
    val invocation = createRenderChildInvocation(workflow, "props", "key")

    assertSame(workflow, invocation.workflow)
    assertEquals("props", invocation.props)
    assertEquals(typeOf<Int>(), invocation.outputType.type)
    assertEquals(typeOf<Unit>(), invocation.renderingType.type)
    assertEquals("key", invocation.renderKey)
  }

  @Test fun `createRenderChildInvocation() for anonymous StatelessWorkflow`() {
    val workflow = object : StatelessWorkflow<String, Int, Unit>() {
      override fun render(
        props: String,
        context: RenderContext
      ) = throw NotImplementedError()
    }
    val invocation = createRenderChildInvocation(workflow, "props", "key")

    assertSame(workflow, invocation.workflow)
    assertEquals("props", invocation.props)
    assertEquals(typeOf<Int>(), invocation.outputType.type)
    assertEquals(typeOf<Unit>(), invocation.renderingType.type)
    assertEquals("key", invocation.renderKey)
  }

  @Test fun `createRenderChildInvocation() for non-anonymous StatefulWorkflow`() {
    class TestWorkflow : StatefulWorkflow<String, Double, Int, Unit>() {
      override fun initialState(
        props: String,
        snapshot: Snapshot?
      ): Double = throw NotImplementedError()

      override fun render(
        props: String,
        state: Double,
        context: RenderContext
      ) = throw NotImplementedError()

      override fun snapshotState(state: Double): Snapshot? = throw NotImplementedError()
    }

    val workflow = TestWorkflow()
    val invocation = createRenderChildInvocation(workflow, "props", "key")

    assertSame(workflow, invocation.workflow)
    assertEquals("props", invocation.props)
    assertEquals(typeOf<Int>(), invocation.outputType.type)
    assertEquals(typeOf<Unit>(), invocation.renderingType.type)
    assertEquals("key", invocation.renderKey)
  }

  @Test fun `createRenderChildInvocation() for non-anonymous StatelessWorkflow`() {
    class TestWorkflow : StatelessWorkflow<String, Int, Unit>() {
      override fun render(
        props: String,
        context: RenderContext
      ) = throw NotImplementedError()
    }

    val workflow = TestWorkflow()
    val invocation = createRenderChildInvocation(workflow, "props", "key")

    assertSame(workflow, invocation.workflow)
    assertEquals("props", invocation.props)
    assertEquals(typeOf<Int>(), invocation.outputType.type)
    assertEquals(typeOf<Unit>(), invocation.renderingType.type)
    assertEquals("key", invocation.renderKey)
  }

  @Test fun `realTypeMatchesExpectation() matches exact type`() {
    val expected = unsnapshottableIdentifier(typeOf<InvariantGenericType<String>>())
    val actual = unsnapshottableIdentifier(typeOf<InvariantGenericType<String>>())
    assertTrue(actual.realTypeMatchesExpectation(expected))
  }

  @Test fun `realTypeMatchesExpectation() doesn't match unrelated type`() {
    val expected = unsnapshottableIdentifier(typeOf<String>())
    val actual = unsnapshottableIdentifier(typeOf<Int>())
    assertFalse(actual.realTypeMatchesExpectation(expected))
  }

  @Test fun `realTypeMatchesExpectation() doesn't match unrelated type parameter`() {
    val expected = unsnapshottableIdentifier(typeOf<InvariantGenericType<String>>())
    val actual = unsnapshottableIdentifier(typeOf<InvariantGenericType<Int>>())
    assertFalse(actual.realTypeMatchesExpectation(expected))
  }

  @Test
  fun `realTypeMatchesExpectation() doesn't match exact invariant type with supertype parameter`() {
    val expected = unsnapshottableIdentifier(typeOf<InvariantGenericType<CharSequence>>())
    val actual = unsnapshottableIdentifier(typeOf<InvariantGenericType<String>>())
    assertFalse(actual.realTypeMatchesExpectation(expected))
  }

  @Test
  fun `realTypeMatchesExpectation() doesn't match exact invariant type with subtype parameter`() {
    val expected = unsnapshottableIdentifier(typeOf<InvariantGenericType<String>>())
    val actual = unsnapshottableIdentifier(typeOf<InvariantGenericType<CharSequence>>())
    assertFalse(actual.realTypeMatchesExpectation(expected))
  }

  @Test fun `realTypeMatchesExpectation() matches exact covariant type with supertype parameter`() {
    val expected = unsnapshottableIdentifier(typeOf<CovariantGenericType<CharSequence>>())
    val actual = unsnapshottableIdentifier(typeOf<CovariantGenericType<String>>())
    assertTrue(actual.realTypeMatchesExpectation(expected))
  }

  @Test
  fun `realTypeMatchesExpectation() doesn't match exact covariant type with subtype parameter`() {
    val expected = unsnapshottableIdentifier(typeOf<CovariantGenericType<String>>())
    val actual = unsnapshottableIdentifier(typeOf<CovariantGenericType<CharSequence>>())
    assertFalse(actual.realTypeMatchesExpectation(expected))
  }

  @Test
  fun `realTypeMatchesExpectation() doesn't match exact contravariant type with supertype parameter`() {
    val expected = unsnapshottableIdentifier(typeOf<ContravariantGenericType<CharSequence>>())
    val actual = unsnapshottableIdentifier(typeOf<ContravariantGenericType<String>>())
    assertFalse(actual.realTypeMatchesExpectation(expected))
  }

  @Test
  fun `realTypeMatchesExpectation() matches exact contravariant type with subtype parameter`() {
    val expected = unsnapshottableIdentifier(typeOf<ContravariantGenericType<String>>())
    val actual = unsnapshottableIdentifier(typeOf<ContravariantGenericType<CharSequence>>())
    assertTrue(actual.realTypeMatchesExpectation(expected))
  }

  @Test fun `realTypeMatchesExpectation() matches exact class`() {
    val expected = TestWorkflow.identifier
    val actual = TestWorkflow.identifier
    assertTrue(actual.realTypeMatchesExpectation(expected))
  }

  @Test fun `realTypeMatchesExpectation() matches superclass`() {
    val expected = Workflow::class.workflowIdentifier
    val actual = TestWorkflow.identifier
    assertTrue(actual.realTypeMatchesExpectation(expected))
  }

  @Test fun `realTypeMatchesExpectation() doesn't match subclass`() {
    val expected = TestWorkflow.identifier
    val actual = Workflow::class.workflowIdentifier
    assertFalse(actual.realTypeMatchesExpectation(expected))
  }

  private object TestWorkflow : Workflow<Nothing, Nothing, Nothing> {
    override fun asStatefulWorkflow(): StatefulWorkflow<Nothing, *, Nothing, Nothing> =
      throw NotImplementedError()
  }

  @Suppress("unused")
  private interface InvariantGenericType<T>

  @Suppress("unused")
  private interface CovariantGenericType<out T>

  @Suppress("unused")
  private interface ContravariantGenericType<in T>
}
