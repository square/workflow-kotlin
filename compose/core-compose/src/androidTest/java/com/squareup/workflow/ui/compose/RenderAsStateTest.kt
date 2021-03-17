/*
 * Copyright 2020 Square Inc.
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
@file:Suppress("RemoveEmptyParenthesesFromAnnotationEntry")

package com.squareup.workflow.ui.compose

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.action
import com.squareup.workflow1.parse
import com.squareup.workflow1.readUtf8WithLength
import com.squareup.workflow1.stateless
import com.squareup.workflow.ui.compose.RenderAsStateTest.SnapshottingWorkflow.SnapshottedRendering
import com.squareup.workflow1.writeUtf8WithLength
import okio.ByteString
import okio.ByteString.Companion.decodeBase64
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RenderAsStateTest {

  @get:Rule val composeRule = createComposeRule()

  @Test fun passesPropsThrough() {
    val workflow = Workflow.stateless<String, Nothing, String> { it }
    lateinit var initialRendering: String

    composeRule.setContent {
      initialRendering = workflow.renderAsState("foo").value
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
      rendering = workflow.renderAsState(props.value).value
    }

    composeRule.waitForIdle()
    assertThat(rendering).isEqualTo("foo")
    props.value = "bar"
    composeRule.waitForIdle()
    assertThat(rendering).isEqualTo("bar")
  }

  @Test fun invokesOutputCallback() {
    val workflow = Workflow.stateless<Unit, String, (String) -> Unit> {
      { string -> actionSink.send(action { setOutput(string) }) }
    }
    val receivedOutputs = mutableListOf<String>()
    lateinit var rendering: (String) -> Unit

    composeRule.setContent {
      rendering = workflow.renderAsState(onOutput = { receivedOutputs += it }).value
    }

    composeRule.waitForIdle()
    assertThat(receivedOutputs).isEmpty()
    rendering("one")

    composeRule.waitForIdle()
    assertThat(receivedOutputs).isEqualTo(listOf("one"))
    rendering("two")

    composeRule.waitForIdle()
    assertThat(receivedOutputs).isEqualTo(listOf("one", "two"))
  }

  @Test fun savesSnapshot() {
    val workflow = SnapshottingWorkflow()
    val savedStateRegistry = SaveableStateRegistry(emptyMap()) { true }
    lateinit var rendering: SnapshottedRendering

    composeRule.setContent {
      CompositionLocalProvider(LocalSaveableStateRegistry provides savedStateRegistry) {
        rendering = renderAsStateImpl(
            workflow,
            props = Unit,
            interceptors = emptyList(),
            onOutput = {},
            snapshotKey = SNAPSHOT_KEY
        ).value
      }
    }

    composeRule.waitForIdle()
    assertThat(rendering.string).isEmpty()
    rendering.updateString("foo")

    composeRule.waitForIdle()
    val savedValues = savedStateRegistry.performSave()
    println("saved keys: ${savedValues.keys}")
    // Relying on the int key across all runtimes is brittle, so use an explicit key.
    @Suppress("UNCHECKED_CAST")
    val snapshot =
      ByteString.of(*((savedValues.getValue(SNAPSHOT_KEY).single() as State<ByteArray>).value))
    println("snapshot: ${snapshot.base64()}")
    assertThat(snapshot).isEqualTo(EXPECTED_SNAPSHOT)
  }

  @Test fun restoresSnapshot() {
    val workflow = SnapshottingWorkflow()
    val restoreValues =
      mapOf(SNAPSHOT_KEY to listOf(mutableStateOf(EXPECTED_SNAPSHOT.toByteArray())))
    val savedStateRegistry = SaveableStateRegistry(restoreValues) { true }
    lateinit var rendering: SnapshottedRendering

    composeRule.setContent {
      CompositionLocalProvider(LocalSaveableStateRegistry provides savedStateRegistry) {
        rendering = renderAsStateImpl(
            workflow,
            props = Unit,
            interceptors = emptyList(),
            onOutput = {},
            snapshotKey = "workflow-snapshot"
        ).value
      }
    }

    composeRule.waitForIdle()
    assertThat(rendering.string).isEqualTo("foo")
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
      rendering = currentWorkflow.value.renderAsState().value
    }

    // Initialize the first workflow.
    composeRule.waitForIdle()
    assertThat(rendering.string).isEmpty()
    assertWasRecomposed()
    rendering.updateString("one")
    composeRule.waitForIdle()
    assertWasRecomposed()
    assertThat(rendering.string).isEqualTo("one")

    // Change the workflow instance being rendered. This should restart the runtime, but restore
    // it from the snapshot.
    currentWorkflow.value = workflow2

    composeRule.waitForIdle()
    assertWasRecomposed()
    assertThat(rendering.string).isEqualTo("one")
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
