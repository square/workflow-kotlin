package com.squareup.sample.todo

import android.text.style.StrikethroughSpan
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import com.squareup.sample.todo.TodoEditingSession.RowEditingSession
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

@OptIn(WorkflowUiExperimentalApi::class)
class ItemListView private constructor(private val itemContainer: LinearLayout) {

  private val inflater = LayoutInflater.from(itemContainer.context)
  private val strikethroughSpan = StrikethroughSpan()

  var onDoneClickedListener: (index: Int) -> Unit = {}
  var onDeleteClickedListener: (index: Int) -> Unit = {}

  fun setRows(rows: List<RowEditingSession>) {
    for ((index, row) in rows.withIndex()) addItemRow(index, row)
    pruneDeadRowsFrom(rows.size)
  }

  private fun addItemRow(
    index: Int,
    row: RowEditingSession
  ) {
    val rowView = requireRowView(index)

    val checkBox = rowView.findViewById<CheckBox>(R.id.todo_done)
    val editText = rowView.findViewById<EditText>(R.id.todo_text)
    val deleteButton = rowView.findViewById<View>(R.id.todo_delete)

    checkBox.isChecked = row.checked
    checkBox.setOnClickListener { onDoneClickedListener(index) }

    row.textController.control(editText)

    if (row.checked) {
      editText.text.setSpan(strikethroughSpan, 0, editText.text.length, 0)
    } else {
      editText.text.removeSpan(strikethroughSpan)
    }

    @Suppress("UsePropertyAccessSyntax")
    editText.setOnFocusChangeListener { _, hasFocus ->
      deleteButton.visibility = if (hasFocus) View.VISIBLE else View.GONE
    }

    deleteButton.visibility = if (editText.hasFocus()) View.VISIBLE else View.GONE
    deleteButton.setOnClickListener { onDeleteClickedListener(index) }
  }

  private fun pruneDeadRowsFrom(index: Int) {
    while (itemContainer.childCount > index) itemContainer.removeViewAt(index)
  }

  private fun requireRowView(index: Int): View {
    return if (index < itemContainer.childCount) {
      itemContainer.getChildAt(index)
    } else {
      inflater.inflate(R.layout.todo_item_layout, itemContainer, false)
        .also { row -> itemContainer.addView(row) }
    }
  }

  companion object {
    fun fromLinearLayout(itemContainer: LinearLayout): ItemListView {
      // We always want to restore view state from the workflow.
      itemContainer.isSaveFromParentEnabled = false
      return ItemListView(itemContainer)
    }
  }
}
