package com.squareup.workflow1.ui

import android.text.Editable
import android.text.Selection.getSelectionEnd
import android.text.Selection.getSelectionStart
import android.text.TextWatcher
import android.widget.EditText

/**
 * Helper for setting the text value of an [EditText] without disrupting the IME connection.
 * If [text] contains a selection, then the selection of this [EditText] is updated to it.
 *
 * Intended to be used by [LayoutRunner]s for updating [EditText]s from workflow renderings.
 */
@WorkflowUiExperimentalApi
fun EditText.updateText(text: CharSequence) {
  if (text.areTextAndSelectionEqual(this.text)) {
    return
  }

  val editable = editableText
  if (editable == null) {
    setText(text)
  } else {
    editable.replace(0, editable.length, text)
  }

  val textSelection = text.selection
  if (textSelection != NO_SELECTION) {
    setSelection(textSelection.first, textSelection.last)
  }
}

/**
 * Helper for setting a simple function as a callback to be invoked whenever an [EditText] text
 * value changes. Simpler than manually invoking [EditText.removeTextChangedListener] and
 * [EditText.addTextChangedListener] and implementing a whole [TextWatcher] manually.
 *
 * Intended to be used by [LayoutRunner]s for updating [EditText]s from workflow renderings.
 */
@WorkflowUiExperimentalApi
fun EditText.setTextChangedListener(listener: ((CharSequence) -> Unit)?) {
  val oldWatcher = getTag(R.id.view_text_changed_listener) as? TextChangedListenerWatcher

  if (listener == null && oldWatcher != null) {
    removeTextChangedListener(oldWatcher)
    return
  }

  if (listener != null) {
    if (oldWatcher == null) {
      TextChangedListenerWatcher(listener).also { watcher ->
        setTag(R.id.view_text_changed_listener, watcher)
        addTextChangedListener(watcher)
      }
    } else {
      oldWatcher.listener = listener
    }
  }
}

private fun CharSequence.areTextAndSelectionEqual(other: CharSequence): Boolean {
  if (toString() != other.toString()) return false

  val thisSelection = selection
  val otherSelection = other.selection

  // Treat "no selection" as equal to any selection.
  if (thisSelection == NO_SELECTION || otherSelection == NO_SELECTION) return true
  return thisSelection == otherSelection
}

private val NO_SELECTION = -1..-1

private val CharSequence.selection: IntRange
  get() = getSelectionStart(this)..getSelectionEnd(this)

private class TextChangedListenerWatcher(var listener: (CharSequence) -> Unit) : TextWatcher {
  override fun onTextChanged(
    s: CharSequence,
    start: Int,
    before: Int,
    count: Int
  ) {
    listener(s)
  }

  override fun beforeTextChanged(
    s: CharSequence?,
    start: Int,
    count: Int,
    after: Int
  ) {
    // Noop
  }

  override fun afterTextChanged(s: Editable?) {
    // Noop
  }
}
