package com.squareup.sample.helloterminal

import com.squareup.sample.helloterminal.HelloTerminalWorkflow.State
import com.squareup.sample.helloterminal.terminalworkflow.ExitCode
import com.squareup.sample.helloterminal.terminalworkflow.KeyStroke
import com.squareup.sample.helloterminal.terminalworkflow.KeyStroke.KeyType.Backspace
import com.squareup.sample.helloterminal.terminalworkflow.TerminalProps
import com.squareup.sample.helloterminal.terminalworkflow.TerminalRendering
import com.squareup.sample.helloterminal.terminalworkflow.TerminalRendering.Color.GREEN
import com.squareup.sample.helloterminal.terminalworkflow.TerminalWorkflow
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.action
import com.squareup.workflow1.renderChild
import com.squareup.workflow1.runningWorker

private typealias HelloTerminalAction = WorkflowAction<TerminalProps, State, ExitCode>

class HelloTerminalWorkflow : TerminalWorkflow,
  StatefulWorkflow<TerminalProps, State, ExitCode, TerminalRendering>() {

  data class State(
    val text: String = ""
  ) {
    fun backspace() = copy(text = text.dropLast(1))
    fun append(char: Char) = copy(text = text + char)
  }

  private val cursorWorkflow = BlinkingCursorWorkflow('_', 500)

  override fun initialState(
    props: TerminalProps,
    snapshot: Snapshot?
  ) = State()

  override fun render(
    renderProps: TerminalProps,
    renderState: State,
    context: StatefulWorkflow.RenderContext<TerminalProps, State, ExitCode>
  ): TerminalRendering {
    val (rows, columns) = renderProps.size
    val header = """
          Hello world!

          Terminal dimensions: $rows rows ⨉ $columns columns


    """.trimIndent()

    val prompt = "> "
    val cursor = context.renderChild(cursorWorkflow)

    context.runningWorker(renderProps.keyStrokes) { onKeystroke(it) }

    return TerminalRendering(
      text = header + prompt + renderState.text + cursor,
      textColor = GREEN
    )
  }

  override fun snapshotState(state: State): Snapshot? = null

  private fun onKeystroke(key: KeyStroke): HelloTerminalAction = action("onKeystroke") {
    when {
      key.character == 'Q' -> setOutput(0)
      key.keyType == Backspace -> state = state.backspace()
      key.character != null -> state = state.append(key.character!!)
    }
  }
}
