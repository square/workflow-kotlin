package com.squareup.sample.hellotodo

import com.squareup.sample.helloterminal.terminalworkflow.ExitCode
import com.squareup.sample.helloterminal.terminalworkflow.KeyStroke
import com.squareup.sample.helloterminal.terminalworkflow.KeyStroke.KeyType.ArrowDown
import com.squareup.sample.helloterminal.terminalworkflow.KeyStroke.KeyType.ArrowUp
import com.squareup.sample.helloterminal.terminalworkflow.KeyStroke.KeyType.Enter
import com.squareup.sample.helloterminal.terminalworkflow.TerminalProps
import com.squareup.sample.helloterminal.terminalworkflow.TerminalRendering
import com.squareup.sample.helloterminal.terminalworkflow.TerminalWorkflow
import com.squareup.sample.hellotodo.EditTextWorkflow.EditTextProps
import com.squareup.sample.hellotodo.TodoWorkflow.TodoList
import com.squareup.sample.hellotodo.TodoWorkflow.TodoList.Companion.TITLE_FIELD_INDEX
import com.squareup.workflow1.BaseRenderContext
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.action
import com.squareup.workflow1.runningWorker

private typealias TodoAction = WorkflowAction<TerminalProps, TodoList, ExitCode>

class TodoWorkflow : TerminalWorkflow,
  StatefulWorkflow<TerminalProps, TodoList, ExitCode, TerminalRendering>() {

  data class TodoList(
    val title: String = "[untitled]",
    val items: List<TodoItem> = emptyList(),
    val focusedField: Int = TITLE_FIELD_INDEX
  ) {

    fun moveFocusUp() = copy(focusedField = (focusedField - 1).coerceAtLeast(TITLE_FIELD_INDEX))
    fun moveFocusDown() = copy(focusedField = (focusedField + 1).coerceAtMost(items.size - 1))
    fun toggleChecked(index: Int) = copy(
      items = items.mapIndexed { i, item ->
        item.copy(checked = item.checked xor (index == i))
      }
    )

    companion object {
      const val TITLE_FIELD_INDEX = -1
    }
  }

  data class TodoItem(
    val label: String,
    val checked: Boolean = false
  )

  override fun initialState(
    props: TerminalProps,
    snapshot: Snapshot?
  ) = TodoList(
    title = "Grocery list",
    items = listOf(
      TodoItem("eggs"),
      TodoItem("cheese"),
      TodoItem("bread"),
      TodoItem("beer")
    )
  )

  override fun render(
    renderProps: TerminalProps,
    renderState: TodoList,
    context: RenderContext
  ): TerminalRendering {
    context.runningWorker(renderProps.keyStrokes) { onKeystroke(it) }

    return TerminalRendering(
      buildString {
        appendLine(renderState.renderTitle(renderProps, context))
        appendLine(renderSelection(renderState.titleSeparator, false))
        appendLine(renderState.renderItems(renderProps, context))
      }
    )
  }

  override fun snapshotState(state: TodoList): Snapshot? = null

  private fun onKeystroke(key: KeyStroke) = action("onKeystroke") {
    when (key.keyType) {
      ArrowUp -> state = state.moveFocusUp()
      ArrowDown -> state = state.moveFocusDown()
      Enter -> if (state.focusedField > TITLE_FIELD_INDEX) {
        state = state.toggleChecked(state.focusedField)
      }
      else -> {}
    }
  }
}

private fun updateTitle(newTitle: String): TodoAction = action("updateTitle") {
  state = state.copy(title = newTitle)
}

private fun setLabel(
  index: Int,
  text: String
): TodoAction = action("setLabel") {
  state = state.copy(
    items = state.items.mapIndexed { i, item ->
      if (index == i) item.copy(label = text) else item
    }
  )
}

private fun TodoList.renderTitle(
  props: TerminalProps,
  context: BaseRenderContext<TerminalProps, TodoList, ExitCode>
): String {
  val isSelected = focusedField == TITLE_FIELD_INDEX
  val titleString = if (isSelected) {
    context.renderChild(
      EditTextWorkflow(),
      props = EditTextProps(title, props),
      key = TITLE_FIELD_INDEX.toString()
    ) { resultStr -> updateTitle(resultStr) }
  } else {
    title
  }
  return renderSelection(titleString, isSelected)
}

private val TodoList.titleSeparator get() = "–".repeat(title.length + 1)

private fun TodoList.renderItems(
  props: TerminalProps,
  context: BaseRenderContext<TerminalProps, TodoList, ExitCode>
): String =
  items
    .mapIndexed { index, item ->
      val check = if (item.checked) '✔' else ' '
      val isSelected = index == focusedField
      val label = if (isSelected) {
        context.renderChild(
          EditTextWorkflow(),
          props = EditTextProps(item.label, props),
          key = index.toString()
        ) { newText -> setLabel(index, newText) }
      } else {
        item.label
      }
      renderSelection("[$check] $label", isSelected)
    }
    .joinToString(separator = "\n")

private fun renderSelection(
  text: String,
  selected: Boolean
): String {
  val prefix = if (selected) "> " else "  "
  return prefix + text
}
