@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)

package com.squareup.workflow1.ui.compose

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.StateRestorationTester
import androidx.compose.ui.test.runComposeUiTest
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.action
import com.squareup.workflow1.parse
import com.squareup.workflow1.readUtf8WithLength
import com.squareup.workflow1.rendering
import com.squareup.workflow1.stateless
import com.squareup.workflow1.ui.compose.RenderAsStateTestIos.SnapshottingWorkflow.SnapshottedRendering
import com.squareup.workflow1.writeUtf8WithLength
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.ByteString
import okio.ByteString.Companion.decodeBase64
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class RenderAsStateTestIos {

  @Test fun passesPropsThrough() = runComposeUiTest {
    val workflow = Workflow.stateless<String, Nothing, String> { it }
    lateinit var initialRendering: String

    setContentWithLifecycle {
      initialRendering = workflow.renderAsState(props = "foo", onOutput = {}).value
    }

    runOnIdle {
      assertEquals("foo", initialRendering)
    }
  }

  @Test fun seesPropsAndRenderingUpdates() = runComposeUiTest {
    val workflow = Workflow.stateless<String, Nothing, String> { it }
    val props = mutableStateOf("foo")
    lateinit var rendering: String

    setContentWithLifecycle {
      rendering = workflow.renderAsState(props.value, onOutput = {}).value
    }

    runOnIdle {
      assertEquals("foo", rendering)
      props.value = "bar"
    }
    runOnIdle {
      assertEquals("bar", rendering)
    }
  }

  @Test fun invokesOutputCallback() = runComposeUiTest {
    val workflow = Workflow.stateless<Unit, String, (String) -> Unit> {
      { string ->
        actionSink.send(action { setOutput(string) })
      }
    }
    val receivedOutputs = mutableListOf<String>()
    lateinit var rendering: (String) -> Unit

    setContentWithLifecycle {
      rendering = workflow.renderAsState(props = Unit, onOutput = { receivedOutputs += it }).value
    }

    runOnIdle {
      assertTrue { receivedOutputs.isEmpty() }
      rendering("one")
    }

    runOnIdle {
      assertEquals(listOf("one"), receivedOutputs)
      rendering("two")
    }

    runOnIdle {
      assertEquals(listOf("one", "two"), receivedOutputs)
    }
  }

  @Test fun savesSnapshot() = runComposeUiTest {
    val workflow = SnapshottingWorkflow()
    val savedStateRegistry = SaveableStateRegistry(emptyMap()) { true }
    lateinit var rendering: SnapshottedRendering
    val scope = TestScope()

    setContentWithLifecycle {
      CompositionLocalProvider(LocalSaveableStateRegistry provides savedStateRegistry) {
        rendering = renderAsState(
          workflow = workflow,
          scope = scope,
          props = Unit,
          interceptors = emptyList(),
          onOutput = {},
          snapshotKey = SNAPSHOT_KEY
        ).value
      }
    }

    runOnIdle {
      assertTrue { rendering.string.isEmpty() }
      rendering.updateString("foo")
    }

    // Move along the Workflow.
    scope.advanceUntilIdle()

    runOnIdle {
      val savedValues = savedStateRegistry.performSave()
      println("saved keys: ${savedValues.keys}")
      // Relying on the int key across all runtimes is brittle, so use an explicit key.
      @Suppress("UNCHECKED_CAST")
      val snapshot =
        ByteString.of(*((savedValues.getValue(SNAPSHOT_KEY).single() as State<ByteArray>).value))
      println("snapshot: ${snapshot.base64()}")
      assertEquals(EXPECTED_SNAPSHOT, snapshot)
    }
  }

  @Test fun restoresSnapshot() = runComposeUiTest {
    val workflow = SnapshottingWorkflow()
    val restoreValues =
      mapOf(SNAPSHOT_KEY to listOf(mutableStateOf(EXPECTED_SNAPSHOT.toByteArray())))
    val savedStateRegistry = SaveableStateRegistry(restoreValues) { true }
    lateinit var rendering: SnapshottedRendering

    setContentWithLifecycle {
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

    runOnIdle {
      assertEquals("foo", rendering.string)
    }
  }

  // This test can't run because we can't provide a LocalSaveableStateRegistry in the test due to
  // how StateRestorationTester is setup
  @Ignore
  @Test fun savesAndRestoresSnapshotOnConfigChange() = runComposeUiTest {
    val stateRestorationTester = StateRestorationTester(this)
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

      runOnIdle {
        assertTrue { rendering.string.isEmpty() }
        rendering.updateString("foo")
      }

      // Move along workflow before saving state!
      scope.advanceUntilIdle()

      stateRestorationTester.emulateSaveAndRestore()

      runOnIdle {
        assertEquals("foo", rendering.string)
      }
    }
  }

  @Test fun restoresFromSnapshotWhenWorkflowChanged() = runComposeUiTest {
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
      assertTrue { compositionCount > lastCompositionCount }
      lastCompositionCount = compositionCount
    }

    setContentWithLifecycle {
      compositionCount++
      rendering =
        currentWorkflow.value.renderAsState(props = Unit, onOutput = {}, scope = scope).value
    }

    // Initialize the first workflow.
    runOnIdle {
      assertTrue { rendering.string.isEmpty() }
      assertWasRecomposed()
      rendering.updateString("one")
    }

    // Move along the workflow.
    scope.advanceUntilIdle()

    runOnIdle {
      assertWasRecomposed()
      assertEquals("one", rendering.string)
    }

    // Change the workflow instance being rendered. This should restart the runtime, but restore
    // it from the snapshot.
    currentWorkflow.value = workflow2

    scope.advanceUntilIdle()

    runOnIdle {
      assertWasRecomposed()
      assertEquals("one", rendering.string)
    }
  }

  @Test fun renderingIsAvailableImmediatelyWhenWorkflowScopeUsesDifferentDispatcher() =
    runComposeUiTest {
      val workflow = Workflow.rendering("hello")
      val scope = TestScope()

      setContentWithLifecycle {
        val initialRendering = workflow.renderAsState(
          props = Unit,
          onOutput = {},
          scope = scope
        )
        assertTrue { initialRendering.value.isNotEmpty() }
      }
    }

  @Test fun runtimeIsCancelledWhenCompositionFails() = runComposeUiTest {
    var innerJob: Job? = null
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      runningSideEffect("test") {
        innerJob = coroutineContext.job
        awaitCancellation()
      }
    }
    val scope = TestScope(UnconfinedTestDispatcher())

    class CancelCompositionException : RuntimeException()

    scope.runTest {
      assertFailsWith<CancelCompositionException> {
        setContentWithLifecycle {
          workflow.renderAsState(props = Unit, onOutput = {}, scope = scope)
          throw CancelCompositionException()
        }
      }

      runOnIdle {
        assertNotNull(innerJob)
        assertTrue { innerJob!!.isCancelled }
      }
    }
  }

  @Test fun workflowScopeIsNotCancelledWhenRemovedFromComposition() = runComposeUiTest {
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {}
    val scope = TestScope()
    var shouldRunWorkflow by mutableStateOf(true)

    scope.runTest {
      setContentWithLifecycle {
        if (shouldRunWorkflow) {
          workflow.renderAsState(props = Unit, onOutput = {}, scope = scope)
        }
      }

      runOnIdle {
        assertTrue { scope.isActive }
      }

      shouldRunWorkflow = false

      runOnIdle {
        scope.advanceUntilIdle()
        assertTrue { scope.isActive }
      }
    }
  }

  @Test fun runtimeIsCancelledWhenRemovedFromComposition() = runComposeUiTest {
    var innerJob: Job? = null
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      runningSideEffect("test") {
        innerJob = coroutineContext.job
        awaitCancellation()
      }
    }
    var shouldRunWorkflow by mutableStateOf(true)

    setContentWithLifecycle {
      if (shouldRunWorkflow) {
        workflow.renderAsState(props = Unit, onOutput = {})
      }
    }

    runOnIdle {
      assertNotNull(innerJob)
      assertTrue { innerJob!!.isActive }
    }

    shouldRunWorkflow = false

    runOnIdle {
      assertTrue { innerJob!!.isCancelled }
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
