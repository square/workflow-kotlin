package com.squareup.sample.mainactivity

import android.view.View
import com.squareup.sample.todo.TodoRendering
import com.squareup.sample.todo.databinding.TodoEditorLayoutBinding
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.LayoutRunner
import com.squareup.workflow1.ui.LayoutRunner.Companion.bind
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewFactory
import com.squareup.workflow1.ui.backPressedHandler
import com.squareup.workflow1.ui.backstack.BackStackConfig
import com.squareup.workflow1.ui.backstack.BackStackConfig.Other

@OptIn(WorkflowUiExperimentalApi::class)
internal class TodoEditorLayoutRunner(
  private val binding: TodoEditorLayoutBinding
) : LayoutRunner<TodoRendering> {

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
    rendering: TodoRendering,
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

  companion object : ViewFactory<TodoRendering> by bind(
      TodoEditorLayoutBinding::inflate, ::TodoEditorLayoutRunner
  )
}
