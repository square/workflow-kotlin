package com.squareup.sample.todo

import android.content.Context.INPUT_METHOD_SERVICE
import android.view.View
import android.view.inputmethod.InputMethodManager
import com.squareup.sample.todo.databinding.TodoEditorLayoutBinding
import com.squareup.workflow1.ui.AndroidViewRendering
import com.squareup.workflow1.ui.Compatible
import com.squareup.workflow1.ui.LayoutRunner
import com.squareup.workflow1.ui.LayoutRunner.Companion.bind
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.backPressedHandler
import com.squareup.workflow1.ui.backstack.BackStackConfig
import com.squareup.workflow1.ui.backstack.BackStackConfig.Other
import com.squareup.workflow1.ui.control

@OptIn(WorkflowUiExperimentalApi::class)
data class TodoEditorScreen(
  val session: TodoEditingSession,
  val onCheckboxClicked: (index: Int) -> Unit,
  val onDeleteClicked: (index: Int) -> Unit,
  val onGoBackClicked: () -> Unit
) : AndroidViewRendering<TodoEditorScreen>, Compatible {

  override val compatibilityKey = Compatible.keyFor(this, "${session.id}")
  override val viewFactory = bind(TodoEditorLayoutBinding::inflate, ::TodoEditorLayoutRunner)
}

@OptIn(WorkflowUiExperimentalApi::class)
private class TodoEditorLayoutRunner(
  private val binding: TodoEditorLayoutBinding
) : LayoutRunner<TodoEditorScreen> {

  private val itemListView = ItemListView.fromLinearLayout(binding.itemContainer)

  init {
    with(binding) {
      todoEditorToolbar.setOnClickListener {
        todoTitle.visibility = View.VISIBLE
        todoTitle.requestFocus()
        todoTitle.showSoftKeyboard()
      }

      todoTitle.setOnFocusChangeListener { _, hasFocus ->
        if (!hasFocus) todoTitle.visibility = View.GONE
      }
    }
  }

  override fun showRendering(
    rendering: TodoEditorScreen,
    viewEnvironment: ViewEnvironment
  ) {
    with(binding) {
      todoEditorToolbar.title = rendering.session.title.textValue
      rendering.session.title.control(todoTitle)
      itemListView.setRows(rendering.session.rows)

      if (viewEnvironment[BackStackConfig] == Other) {
        todoEditorToolbar.setNavigationOnClickListener { rendering.onGoBackClicked() }
        root.backPressedHandler = { rendering.onGoBackClicked() }
      } else {
        todoEditorToolbar.navigationIcon = null
      }

      itemListView.onDoneClickedListener = { index ->
        rendering.onCheckboxClicked(index)
      }
      itemListView.onDeleteClickedListener = { index ->
        rendering.onDeleteClicked(index)
      }
    }
  }
}

private fun View.showSoftKeyboard() {
  val inputMethodManager = context.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
  inputMethodManager.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
}
