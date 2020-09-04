package com.squareup.workflow1.ui

import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.renderWorkflowIn
import com.squareup.workflow1.stateless
import com.squareup.workflow1.ui.StateSaver.IntSaver
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.TestCoroutineScope
import java.util.ArrayDeque
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalComposeApi::class)
class MagicWorkflowTest {

  @Test fun stuff() {

    data class MyRendering(
      val str: String,
      val incState: () -> Unit,
      val emitOutput: (Any) -> Unit
    )

    val child = Workflow.stateless<String, Nothing, String> { props -> "<$props>" }

    val myWorkflow: Workflow<Int, Any, MyRendering> = magicWorkflow {
      var intState by state(IntSaver) { 42 }

      rendering { props ->
        val childR = child.render("child:$props") {}

        MyRendering(
            str = "$intState $childR",
            incState = { intState = intState + 1 },
            emitOutput = { sendOutput(it) }
        )
      }
    }

    val scope = TestCoroutineScope()
    val props = MutableStateFlow(0)
    val outputs = ArrayDeque<Any>()
    val renderings = ArrayDeque<MyRendering>()
    renderWorkflowIn(myWorkflow, scope, props) {
      outputs += it
    }.onEach { renderings += it.rendering }
        .launchIn(scope)

    renderings.remove().let {
      assertThat(renderings).isEmpty()
      assertThat(outputs).isEmpty()
      assertThat(it.str).isEqualTo("42 <child:0>")
      it.incState()
    }

    renderings.remove().let {
      assertThat(renderings).isEmpty()
      assertThat(outputs).isEmpty()
      assertThat(it.str).isEqualTo("43 <child:0>")
      props.value = 100
    }

    renderings.remove().let {
      assertThat(renderings).isEmpty()
      assertThat(outputs).isEmpty()
      assertThat(it.str).isEqualTo("43 <child:100>")
    }
  }
}
