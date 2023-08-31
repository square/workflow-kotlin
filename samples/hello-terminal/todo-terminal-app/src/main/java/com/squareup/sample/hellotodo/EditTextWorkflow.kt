package com.squareup.sample.hellotodo

import com.squareup.sample.helloterminal.terminalworkflow.KeyStroke
import com.squareup.sample.helloterminal.terminalworkflow.KeyStroke.KeyType.ArrowLeft
import com.squareup.sample.helloterminal.terminalworkflow.KeyStroke.KeyType.ArrowRight
import com.squareup.sample.helloterminal.terminalworkflow.KeyStroke.KeyType.Backspace
import com.squareup.sample.helloterminal.terminalworkflow.KeyStroke.KeyType.Character
import com.squareup.sample.helloterminal.terminalworkflow.TerminalProps
import com.squareup.sample.hellotodo.EditTextWorkflow.EditTextProps
import com.squareup.sample.hellotodo.EditTextWorkflow.EditTextState
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.WorkflowLocal
import com.squareup.workflow1.action
import com.squareup.workflow1.runningWorker

class EditTextWorkflow : StatefulWorkflow<EditTextProps, EditTextState, String, String>() {

  data class EditTextProps(
    val text: String,
    val terminalProps: TerminalProps
  )

  /**
   * @param cursorPosition The index before which to draw the cursor (so 0 means before the first
   * character, `length` means after the last character).
   */
  data class EditTextState(
    val cursorPosition: Int
  )

  override fun initialState(
    props: EditTextProps,
    snapshot: Snapshot?,
    workflowLocal: WorkflowLocal
  ) = EditTextState(cursorPosition = props.text.length)

  override fun onPropsChanged(
    old: EditTextProps,
    new: EditTextProps,
    state: EditTextState
  ): EditTextState {
    return if (old.text != new.text) {
      // Clamp the cursor position to the text length.
      state.copy(cursorPosition = state.cursorPosition.coerceIn(new.text))
    } else {
      state
    }
  }

  override fun render(
    renderProps: EditTextProps,
    renderState: EditTextState,
    context: RenderContext
  ): String {
    context.runningWorker(
      renderProps.terminalProps.keyStrokes
    ) { key -> onKeystroke(key) }

    return buildString {
      renderProps.text.forEachIndexed { index, c ->
        append(if (index == renderState.cursorPosition) "|$c" else "$c")
      }
      if (renderState.cursorPosition == renderProps.text.length) append("|")
    }
  }

  override fun snapshotState(state: EditTextState): Snapshot? = null

  private fun onKeystroke(
    key: KeyStroke
  ) = action {
    when (key.keyType) {
      Character -> {
        val newText = props.text.insertCharAt(state.cursorPosition, key.character!!)
        setOutput(newText)
        state = moveCursor(newText, state, 1)
      }
      Backspace -> {
        if (props.text.isNotEmpty() && state.cursorPosition > 0) {
          val newText = props.text.removeRange(state.cursorPosition - 1, state.cursorPosition)
          setOutput(newText)
          state = moveCursor(newText, state, -1)
        }
      }
      ArrowLeft -> state = moveCursor(props.text, state, -1)
      ArrowRight -> state = moveCursor(props.text, state, 1)
      else -> {
        // Nothing to do.
      }
    }
  }
}

private fun moveCursor(
  text: String,
  state: EditTextState,
  delta: Int
): EditTextState =
  state.copy(cursorPosition = (state.cursorPosition + delta).coerceIn(text))

private fun String.insertCharAt(
  index: Int,
  char: Char
): String = substring(0, index) + char + substring(index, length)

private fun Int.coerceIn(text: String): Int = coerceIn(0..text.length)
