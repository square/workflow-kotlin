package com.squareup.sample.todo

import android.view.View
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

@OptIn(WorkflowUiExperimentalApi::class)
data class TodoEditorScreen(
  val list: TodoList,
  val onTitleChanged: (title: String) -> Unit,
  val onDoneClicked: (index: Int) -> Unit,
  val onTextChanged: (index: Int, text: String) -> Unit,
  val onDeleteClicked: (index: Int) -> Unit,
  val onGoBackClicked: () -> Unit
) : AndroidViewRendering<TodoEditorScreen>, Compatible {
  override val compatibilityKey = Compatible.keyFor(this, "${list.id}")
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

      @Suppress("UsePropertyAccessSyntax")
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
      todoEditorToolbar.title = rendering.list.title
      todoTitle.text.replace(0, todoTitle.text.length, rendering.list.title)
      itemListView.setRows(rendering.list.rows.map { Pair(it.done, it.text) })

      if (viewEnvironment[BackStackConfig] == Other) {
        todoEditorToolbar.setNavigationOnClickListener { rendering.onGoBackClicked() }
        root.backPressedHandler = { rendering.onGoBackClicked() }
      } else {
        todoEditorToolbar.navigationIcon = null
      }

      todoTitle.setTextChangedListener { rendering.onTitleChanged(it) }

      itemListView.onDoneClickedListener = { index ->
        rendering.onDoneClicked(index)
      }
      itemListView.onTextChangedListener = { index, text ->
        rendering.onTextChanged(index, text)
      }
      itemListView.onDeleteClickedListener = { index ->
        rendering.onDeleteClicked(index)
      }
    }
  }
}
