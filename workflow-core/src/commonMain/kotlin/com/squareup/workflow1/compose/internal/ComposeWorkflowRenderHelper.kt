@file:OptIn(WorkflowExperimentalApi::class)

package com.squareup.workflow1.compose.internal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.NonSkippableComposable
import androidx.compose.runtime.remember
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.WorkflowExperimentalApi
import com.squareup.workflow1.action
import com.squareup.workflow1.compose.ComposeWorkflow
import com.squareup.workflow1.compose.renderChild

/**
 * Converts a [ComposeWorkflow] to a [StatefulWorkflow] by implementing [StatefulWorkflow.render] to
 * call [ComposeWorkflow.produceRendering] via a [Trapdoor]. This is a very sharp-edged API and must
 * be used with care! The returned [StatefulWorkflow] must only have its render method called in the
 * same composition as this function is called.
 *
 * The returned [StatefulWorkflow] cannot be ran by a normal workflow runtime, it is only valid for
 * rendering by [renderChild].
 */
// @InternalWorkflowApi
@NonRestartableComposable
@NonSkippableComposable
@Composable
fun <P, O, R> _UNSAFE_rememberComposeWorkflowAsStatefulWorkflow(
  composeWorkflow: ComposeWorkflow<P, O, R>
): StatefulWorkflow<P, Unit, O, R> {
  val trapdoor = Trapdoor.open()
  return remember(composeWorkflow) {
    StatefulComposeWorkflow(
      composeWorkflow = composeWorkflow,
      trapdoor = trapdoor,
    )
  }
}

private class StatefulComposeWorkflow<P, O, R>(
  private val composeWorkflow: ComposeWorkflow<P, O, R>,
  private val trapdoor: Trapdoor,
) : StatefulWorkflow<P, Unit, O, R>() {
  override fun initialState(
    props: P,
    snapshot: Snapshot?
  ) {
  }

  override fun render(
    renderProps: P,
    renderState: Unit,
    context: RenderContext<P, Unit, O>
  ): R = trapdoor.composeReturning {
    val emitOutput = remember(context) {
      fun(output: O) {
        // When rendered by renderChild, the output sink will just send it to emitOutput.
        context.actionSink.send(action("emitOutput") { setOutput(output) })
      }
    }
    composeWorkflow.invokeProduceRendering(
      props = renderProps,
      emitOutput = emitOutput
    )
  }

  override fun snapshotState(state: Unit): Snapshot? = null
}
