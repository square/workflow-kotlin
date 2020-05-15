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

import androidx.compose.FrameManager
import androidx.compose.Providers
import androidx.compose.mutableStateOf
import androidx.compose.onActive
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.ui.foundation.Clickable
import androidx.ui.foundation.Text
import androidx.ui.layout.Column
import androidx.ui.savedinstancestate.UiSavedStateRegistry
import androidx.ui.savedinstancestate.UiSavedStateRegistryAmbient
import androidx.ui.test.createComposeRule
import androidx.ui.test.doClick
import androidx.ui.test.findByText
import androidx.ui.test.waitForIdle
import com.google.common.truth.Truth.assertThat
import com.squareup.workflow.RenderContext
import com.squareup.workflow.Snapshot
import com.squareup.workflow.StatefulWorkflow
import com.squareup.workflow.Workflow
import com.squareup.workflow.action
import com.squareup.workflow.parse
import com.squareup.workflow.readUtf8WithLength
import com.squareup.workflow.stateless
import com.squareup.workflow.ui.compose.WorkflowContainerTest.SnapshottingWorkflow.SnapshottedRendering
import com.squareup.workflow.writeUtf8WithLength
import okio.ByteString
import okio.ByteString.Companion.decodeBase64
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkflowContainerTest {

  @Rule @JvmField val composeRule = createComposeRule()

  @Test fun passesPropsThrough() {
    val workflow = Workflow.stateless<String, Nothing, String> { it }

    composeRule.setContent {
      WorkflowContainer(workflow, "foo") {
        assertThat(it).isEqualTo("foo")
      }
    }
  }

  @Test fun seesPropsAndRenderingUpdates() {
    val workflow = Workflow.stateless<String, Nothing, String> { it }
    val props = mutableStateOf("foo")

    composeRule.setContent {
      WorkflowContainer(workflow, props.value) {
        Text(it)
      }
    }

    findByText("foo").assertExists()
    FrameManager.framed {
      props.value = "bar"
    }
    findByText("bar").assertExists()
  }

  @Test fun invokesOutputCallback() {
    val workflow = Workflow.stateless<Unit, String, (String) -> Unit> {
      { string -> actionSink.send(action { setOutput(string) }) }
    }

    val receivedOutputs = mutableListOf<String>()
    composeRule.setContent {
      WorkflowContainer(workflow, onOutput = { receivedOutputs += it }) { sendOutput ->
        Column {
          Clickable(onClick = { sendOutput("one") }) {
            Text("send one")
          }
          Clickable(onClick = { sendOutput("two") }) {
            Text("send two")
          }
        }
      }
    }

    waitForIdle()
    assertThat(receivedOutputs).isEmpty()
    findByText("send one").doClick()

    waitForIdle()
    assertThat(receivedOutputs).isEqualTo(listOf("one"))
    findByText("send two").doClick()

    waitForIdle()
    assertThat(receivedOutputs).isEqualTo(listOf("one", "two"))
  }

  @Test fun savesSnapshot() {
    val savedStateRegistry = UiSavedStateRegistry(emptyMap()) { true }

    composeRule.setContent {
      Providers(UiSavedStateRegistryAmbient provides savedStateRegistry) {
        WorkflowContainerImpl(
            SnapshottingWorkflow,
            props = Unit,
            onOutput = {},
            snapshotKey = SNAPSHOT_KEY
        ) { (string, updateString) ->
          onActive {
            assertThat(string).isEmpty()
            updateString("foo")
          }
        }
      }
    }

    waitForIdle()
    val savedValues = FrameManager.framed {
      savedStateRegistry.performSave()
    }
    println("saved keys: ${savedValues.keys}")
    // Relying on the int key across all runtimes might be flaky, might need to pass explicit key.
    val snapshot = ByteString.of(*(savedValues.getValue(SNAPSHOT_KEY) as ByteArray))
    println("snapshot: ${snapshot.base64()}")
    assertThat(snapshot).isEqualTo(EXPECTED_SNAPSHOT)
  }

  @Test fun restoresSnapshot() {
    val restoreValues = mapOf(SNAPSHOT_KEY to EXPECTED_SNAPSHOT.toByteArray())
    val savedStateRegistry = UiSavedStateRegistry(restoreValues) { true }

    composeRule.setContent {
      Providers(UiSavedStateRegistryAmbient provides savedStateRegistry) {
        WorkflowContainerImpl(
            SnapshottingWorkflow,
            props = Unit,
            onOutput = {},
            snapshotKey = "workflow-snapshot"
        ) { (string) ->
          onActive {
            assertThat(string).isEqualTo("foo")
          }
          Text(string)
        }
      }
    }

    findByText("foo").assertExists()
  }

  private companion object {
    const val SNAPSHOT_KEY = "workflow-snapshot"
    val EXPECTED_SNAPSHOT = "AAAABwAAAANmb28AAAAA".decodeBase64()!!
  }

  // Seems to be a problem accessing Workflow.stateful.
  private object SnapshottingWorkflow :
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
      props: Unit,
      state: String,
      context: RenderContext<String, Nothing>
    ) = SnapshottedRendering(
        string = state,
        updateString = { newString -> context.actionSink.send(updateString(newString)) }
    )

    override fun snapshotState(state: String): Snapshot =
      Snapshot.write { it.writeUtf8WithLength(state) }

    private fun updateString(newString: String) = action {
      nextState = newString
    }
  }
}
