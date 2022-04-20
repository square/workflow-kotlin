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
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.compose.RenderAsStateTest.SnapshottingWorkflow.SnapshottedRendering
import com.squareup.workflow1.ui.internal.test.IdleAfterTestRule
import com.squareup.workflow1.ui.internal.test.IdlingDispatcherRule
import com.squareup.workflow1.ui.internal.test.wrapInLeakCanary
import com.squareup.workflow1.writeUtf8WithLength
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.test.TestCoroutineScope
import okio.ByteString
import okio.ByteString.Companion.decodeBase64
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import kotlin.test.assertFailsWith

@RunWith(AndroidJUnit4::class)
@OptIn(WorkflowUiExperimentalApi::class)
internal class RenderAsStateTest {

  private val composeRule = createComposeRule()
  @get:Rule val rules: RuleChain = RuleChain.outerRule(IdleAfterTestRule)
    .around(composeRule)
    .around(IdlingDispatcherRule)
    .wrapInLeakCanary()

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
      { string -> actionSink.send(action { setOutput(string) }) }
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

    composeRule.setContent {
      CompositionLocalProvider(LocalSaveableStateRegistry provides savedStateRegistry) {
        rendering = renderAsState(
          workflow = workflow,
          scope = rememberCoroutineScope(),
          props = Unit,
          interceptors = emptyList(),
          onOutput = {},
          snapshotKey = SNAPSHOT_KEY
        ).value
      }
    }

    composeRule.runOnIdle {
      assertThat(rendering.string).isEmpty()
      rendering.updateString("foo")
    }

    composeRule.runOnIdle {
      val savedValues = savedStateRegistry.performSave()
      println("saved keys: ${savedValues.keys}")
      // Relying on the int key across all runtimes is brittle, so use an explicit key.
      @Suppress("UNCHECKED_CAST")
      val snapshot =
        ByteString.of(*((savedValues.getValue(SNAPSHOT_KEY).single() as State<ByteArray>).value))
      println("snapshot: ${snapshot.base64()}")
      assertThat(snapshot).isEqualTo(EXPECTED_SNAPSHOT)
    }
  }

  @Test fun restoresSnapshot() {
    val workflow = SnapshottingWorkflow()
    val restoreValues =
      mapOf(SNAPSHOT_KEY to listOf(mutableStateOf(EXPECTED_SNAPSHOT.toByteArray())))
    val savedStateRegistry = SaveableStateRegistry(restoreValues) { true }
    lateinit var rendering: SnapshottedRendering

    composeRule.setContent {
      CompositionLocalProvider(LocalSaveableStateRegistry provides savedStateRegistry) {
        rendering = renderAsState(
          workflow = workflow,
          scope = rememberCoroutineScope(),
          props = Unit,
          interceptors = emptyList(),
          onOutput = {},
          snapshotKey = "workflow-snapshot"
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

    stateRestorationTester.setContent {
      rendering = workflow.renderAsState(
        scope = rememberCoroutineScope(),
        props = Unit,
        interceptors = emptyList(),
        onOutput = {},
      ).value
    }

    composeRule.runOnIdle {
      assertThat(rendering.string).isEmpty()
      rendering.updateString("foo")
    }

    stateRestorationTester.emulateSavedInstanceStateRestore()

    composeRule.runOnIdle {
      assertThat(rendering.string).isEqualTo("foo")
    }
  }

  @Test fun restoresFromSnapshotWhenWorkflowChanged() {
    val workflow1 = SnapshottingWorkflow()
    val workflow2 = SnapshottingWorkflow()
    val currentWorkflow = mutableStateOf(workflow1)
    lateinit var rendering: SnapshottedRendering

    var compositionCount = 0
    var lastCompositionCount = 0
    fun assertWasRecomposed() {
      assertThat(compositionCount).isGreaterThan(lastCompositionCount)
      lastCompositionCount = compositionCount
    }

    composeRule.setContent {
      compositionCount++
      rendering = currentWorkflow.value.renderAsState(props = Unit, onOutput = {}).value
    }

    // Initialize the first workflow.
    composeRule.runOnIdle {
      assertThat(rendering.string).isEmpty()
      assertWasRecomposed()
      rendering.updateString("one")
    }
    composeRule.runOnIdle {
      assertWasRecomposed()
      assertThat(rendering.string).isEqualTo("one")
    }

    // Change the workflow instance being rendered. This should restart the runtime, but restore
    // it from the snapshot.
    currentWorkflow.value = workflow2

    composeRule.runOnIdle {
      assertWasRecomposed()
      assertThat(rendering.string).isEqualTo("one")
    }
  }

  @Test fun renderingIsAvailableImmediatelyWhenWorkflowScopeUsesDifferentDispatcher() {
    val workflow = Workflow.rendering("hello")
    val scope = TestCoroutineScope()

    // Ensure the workflow runtime won't actually run aside from the synchronous first pass.
    scope.pauseDispatcher()

    try {
      composeRule.setContent {
        val initialRendering = workflow.renderAsState(
          props = Unit, onOutput = {},
          scope = scope
        )
        assertThat(initialRendering.value).isNotNull()
      }
    } finally {
      scope.cleanupTestCoroutines()
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
    val scope = TestCoroutineScope()

    class CancelCompositionException : RuntimeException()

    try {
      assertFailsWith<CancelCompositionException> {
        composeRule.setContent {
          workflow.renderAsState(props = Unit, onOutput = {}, scope = scope)
          throw CancelCompositionException()
        }
      }

      composeRule.runOnIdle {
        scope.advanceUntilIdle()

        assertThat(innerJob).isNotNull()
        assertThat(innerJob!!.isCancelled).isTrue()
      }
    } finally {
      scope.cleanupTestCoroutines()
    }
  }

  @Test fun workflowScopeIsNotCancelledWhenRemovedFromComposition() {
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {}
    val scope = TestCoroutineScope(Job())
    var shouldRunWorkflow by mutableStateOf(true)

    try {
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
    } finally {
      scope.cleanupTestCoroutines()
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
    const val SNAPSHOT_KEY = "workflow-snapshot"

    /** Golden value from [savesSnapshot]. */
    val EXPECTED_SNAPSHOT = "AAAABwAAAANmb28AAAAA".decodeBase64()!!
  }

  // Seems to be a problem accessing Workflow.stateful.
  private class SnapshottingWorkflow :
    StatefulWorkflow<Unit, String, Nothing, SnapshottedRendering>() {

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
      context: RenderContext
    ) = SnapshottedRendering(
      string = renderState,
      updateString = { newString -> context.actionSink.send(updateString(newString)) }
    )

    override fun snapshotState(state: String): Snapshot =
      Snapshot.write { it.writeUtf8WithLength(state) }

    private fun updateString(newString: String) = action {
      state = newString
    }
  }
}
