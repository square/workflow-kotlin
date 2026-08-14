@file:OptIn(ExperimentalCoroutinesApi::class)

package com.squareup.workflow1.ui.compose

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.action
import com.squareup.workflow1.parse
import com.squareup.workflow1.readUtf8WithLength
import com.squareup.workflow1.rendering
import com.squareup.workflow1.stateless
import com.squareup.workflow1.ui.compose.RenderAsStateTest.SnapshottingWorkflow.SnapshottedRendering
import com.squareup.workflow1.ui.internal.test.IdleAfterTestRule
import com.squareup.workflow1.ui.internal.test.IdlingDispatcherRule
import com.squareup.workflow1.writeUtf8WithLength
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import leakcanary.DetectLeaksAfterTestSuccess
import okio.ByteString
import okio.ByteString.Companion.decodeBase64
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import kotlin.test.assertFailsWith

@RunWith(AndroidJUnit4::class)
internal class RenderAsStateTest {

  private val composeRule = createComposeRule()

  @get:Rule val rules: RuleChain = RuleChain.outerRule(DetectLeaksAfterTestSuccess())
    .around(IdleAfterTestRule)
    .around(composeRule)
    .around(IdlingDispatcherRule)

  @Test fun passesPropsThrough() {
    val workflow = Workflow.stateless<String, Nothing, String> { it }
    lateinit var initialRendering: String

    composeRule.setContent {
      initialRendering = workflow.renderAsState(props = "foo", onOutput = {}).value
    }

    composeRule.runOnIdle {
      assertThat(initialRendering).isEqualTo("foo")
    }
  }

  @Test fun seesPropsAndRenderingUpdates() {
    val workflow = Workflow.stateless<String, Nothing, String> { it }
    val props = mutableStateOf("foo")
    lateinit var rendering: String

    composeRule.setContent {
      rendering = workflow.renderAsState(props.value, onOutput = {}).value
    }

    composeRule.runOnIdle {
      assertThat(rendering).isEqualTo("foo")
      props.value = "bar"
    }
    composeRule.runOnIdle {
      assertThat(rendering).isEqualTo("bar")
    }
  }

  @Test fun invokesOutputCallback() {
    val workflow = Workflow.stateless<Unit, String, (String) -> Unit> {
      {
          string ->
        actionSink.send(action("") { setOutput(string) })
      }
    }
    val receivedOutputs = mutableListOf<String>()
    lateinit var rendering: (String) -> Unit

    composeRule.setContent {
      rendering = workflow.renderAsState(props = Unit, onOutput = { receivedOutputs += it }).value
    }

    composeRule.runOnIdle {
      assertThat(receivedOutputs).isEmpty()
      rendering("one")
    }

    composeRule.runOnIdle {
      assertThat(receivedOutputs).isEqualTo(listOf("one"))
      rendering("two")
    }

    composeRule.runOnIdle {
      assertThat(receivedOutputs).isEqualTo(listOf("one", "two"))
    }
  }

  @Test fun savesSnapshot() {
    val workflow = SnapshottingWorkflow()
    val savedStateRegistry = SaveableStateRegistry(emptyMap()) { true }
    lateinit var rendering: SnapshottedRendering
    val scope = TestScope()

    composeRule.setContent {
      CompositionLocalProvider(LocalSaveableStateRegistry provides savedStateRegistry) {
        rendering = renderAsState(
          workflow = workflow,
          scope = scope,
          props = Unit,
          interceptors = emptyList(),
          onOutput = {}
        ).value
      }
    }

    composeRule.runOnIdle {
      assertThat(rendering.string).isEmpty()
      rendering.updateString("foo")
    }

    // Move along the Workflow.
    scope.advanceUntilIdle()

    composeRule.runOnIdle {
      val savedValues = savedStateRegistry.performSave()

      // rememberSaveable generates its key from the call site's position in the composition, so
      // the test can't know it. renderAsState saves exactly one value, so just take that one.
      @Suppress("UNCHECKED_CAST")
      val snapshot =
        ByteString.of(*((savedValues.values.single().single() as State<ByteArray>).value))
      println("snapshot: ${snapshot.base64()}")
      assertThat(snapshot).isEqualTo(EXPECTED_SNAPSHOT)
    }
  }

  @Test fun restoresSnapshot() {
    val workflow = SnapshottingWorkflow()
    // rememberSaveable generates its key from the call site's position in the composition, so the
    // test can't seed the restore map with the right key. renderAsState restores exactly one
    // value, so hand it the snapshot whatever key it asks for.
    val savedStateRegistry = object : SaveableStateRegistry by SaveableStateRegistry(
      restoredValues = emptyMap(),
      canBeSaved = { true }
    ) {
      override fun consumeRestored(key: String): Any =
        mutableStateOf(EXPECTED_SNAPSHOT.toByteArray())
    }
    lateinit var rendering: SnapshottedRendering

    composeRule.setContent {
      CompositionLocalProvider(LocalSaveableStateRegistry provides savedStateRegistry) {
        rendering = renderAsState(
          workflow = workflow,
          scope = rememberCoroutineScope(),
          props = Unit,
          interceptors = emptyList(),
          onOutput = {}
        ).value
      }
    }

    composeRule.runOnIdle {
      assertThat(rendering.string).isEqualTo("foo")
    }
  }

  @Test fun savesAndRestoresSnapshotOnConfigChange() {
    val stateRestorationTester = StateRestorationTester(composeRule)
    val workflow = SnapshottingWorkflow()
    lateinit var rendering: SnapshottedRendering
    val scope = TestScope()

    stateRestorationTester.setContent {
      rendering = workflow.renderAsState(
        scope = scope,
        props = Unit,
        interceptors = emptyList(),
        onOutput = {},
      ).value
    }

    composeRule.runOnIdle {
      assertThat(rendering.string).isEmpty()
      rendering.updateString("foo")
    }

    // Move along workflow before saving state!
    scope.advanceUntilIdle()

    stateRestorationTester.emulateSavedInstanceStateRestore()

    composeRule.runOnIdle {
      assertThat(rendering.string).isEqualTo("foo")
    }
  }

  /**
   * Multiple runtimes in the same composition each save and restore their own snapshot, rather
   * than sharing or clobbering each other's.
   */
  @Test fun savesAndRestoresSnapshotsOfSiblingRuntimesIndependently() {
    val stateRestorationTester = StateRestorationTester(composeRule)
    val workflow = SnapshottingWorkflow()
    lateinit var firstRendering: SnapshottedRendering
    lateinit var secondRendering: SnapshottedRendering
    val scope = TestScope()

    stateRestorationTester.setContent {
      firstRendering = workflow.renderAsState(
        scope = scope,
        props = Unit,
        interceptors = emptyList(),
        onOutput = {},
      ).value
      secondRendering = workflow.renderAsState(
        scope = scope,
        props = Unit,
        interceptors = emptyList(),
        onOutput = {},
      ).value
    }

    composeRule.runOnIdle {
      firstRendering.updateString("first")
      secondRendering.updateString("second")
    }

    // Move along workflow before saving state!
    scope.advanceUntilIdle()

    stateRestorationTester.emulateSavedInstanceStateRestore()

    composeRule.runOnIdle {
      assertThat(firstRendering.string).isEqualTo("first")
      assertThat(secondRendering.string).isEqualTo("second")
    }
  }

  @Test fun restoresFromSnapshotWhenWorkflowChanged() {
    val workflow1 = SnapshottingWorkflow()
    val workflow2 = SnapshottingWorkflow()
    val currentWorkflow = mutableStateOf(workflow1)
    lateinit var rendering: SnapshottedRendering
    // Since we have frame timeouts we need to control the scope of the Workflow Runtime as
    // well as the scope of the Recomposer.
    val scope = TestScope()

    var compositionCount = 0
    var lastCompositionCount = 0
    fun assertWasRecomposed() {
      assertThat(compositionCount).isGreaterThan(lastCompositionCount)
      lastCompositionCount = compositionCount
    }

    composeRule.setContent {
      compositionCount++
      rendering =
        currentWorkflow.value.renderAsState(props = Unit, onOutput = {}, scope = scope).value
    }

    // Initialize the first workflow.
    composeRule.runOnIdle {
      assertThat(rendering.string).isEmpty()
      assertWasRecomposed()
      rendering.updateString("one")
    }

    // Move along the workflow.
    scope.advanceUntilIdle()

    composeRule.runOnIdle {
      assertWasRecomposed()
      assertThat(rendering.string).isEqualTo("one")
    }

    // Change the workflow instance being rendered. This should restart the runtime, but restore
    // it from the snapshot.
    currentWorkflow.value = workflow2

    scope.advanceUntilIdle()

    composeRule.runOnIdle {
      assertWasRecomposed()
      assertThat(rendering.string).isEqualTo("one")
    }
  }

  @Test fun renderingIsAvailableImmediatelyWhenWorkflowScopeUsesDifferentDispatcher() {
    val workflow = Workflow.rendering<Nothing, String>("hello")
    val scope = TestScope()

    composeRule.setContent {
      val initialRendering = workflow.renderAsState(
        props = Unit,
        onOutput = {},
        scope = scope
      )
      assertThat(initialRendering.value).isNotNull()
    }
  }

  @Test fun runtimeIsCancelledWhenCompositionFails() {
    var innerJob: Job? = null
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      runningSideEffect("test") {
        innerJob = coroutineContext.job
        awaitCancellation()
      }
    }
    val scope = TestScope(StandardTestDispatcher())

    class CancelCompositionException : RuntimeException()

    scope.runTest {
      assertFailsWith<CancelCompositionException> {
        composeRule.setContent {
          workflow.renderAsState(props = Unit, onOutput = {}, scope = scope)
          scope.advanceUntilIdle()
          throw CancelCompositionException()
        }
      }

      composeRule.runOnIdle {
        assertThat(innerJob).isNotNull()
        assertThat(innerJob!!.isCancelled).isTrue()
      }
    }
  }

  @Test fun workflowScopeIsNotCancelledWhenRemovedFromComposition() {
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {}
    val scope = TestScope()
    var shouldRunWorkflow by mutableStateOf(true)

    scope.runTest {
      composeRule.setContent {
        if (shouldRunWorkflow) {
          workflow.renderAsState(props = Unit, onOutput = {}, scope = scope)
        }
      }

      composeRule.runOnIdle {
        assertThat(scope.isActive).isTrue()
      }

      shouldRunWorkflow = false

      composeRule.runOnIdle {
        scope.advanceUntilIdle()
        assertThat(scope.isActive).isTrue()
      }
    }
  }

  @Test fun runtimeIsCancelledWhenRemovedFromComposition() {
    var innerJob: Job? = null
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      runningSideEffect("test") {
        innerJob = coroutineContext.job
        awaitCancellation()
      }
    }
    var shouldRunWorkflow by mutableStateOf(true)

    composeRule.setContent {
      if (shouldRunWorkflow) {
        workflow.renderAsState(props = Unit, onOutput = {})
      }
    }

    composeRule.runOnIdle {
      assertThat(innerJob).isNotNull()
      assertThat(innerJob!!.isActive).isTrue()
    }

    shouldRunWorkflow = false

    composeRule.runOnIdle {
      assertThat(innerJob!!.isCancelled).isTrue()
    }
  }

  private companion object {
    /** Golden value from [savesSnapshot]. */
    val EXPECTED_SNAPSHOT = "AAAABwAAAANmb28AAAAA".decodeBase64()!!
  }

  // Seems to be a problem accessing Workflow.stateful.
  private class SnapshottingWorkflow : StatefulWorkflow<Unit, String, Nothing, SnapshottedRendering>() {

    data class SnapshottedRendering(
      val string: String,
      val updateString: (String) -> Unit
    )

    override fun initialState(
      props: Unit,
      snapshot: Snapshot?
    ): String = snapshot?.bytes?.parse { it.readUtf8WithLength() } ?: ""

    override fun render(
      renderProps: Unit,
      renderState: String,
      context: RenderContext<Unit, String, Nothing>
    ) = SnapshottedRendering(
      string = renderState,
      updateString = { newString -> context.actionSink.send(updateString(newString)) }
    )

    override fun snapshotState(state: String): Snapshot =
      Snapshot.write { it.writeUtf8WithLength(state) }

    private fun updateString(newString: String) = action("updateString") {
      state = newString
    }
  }
}
