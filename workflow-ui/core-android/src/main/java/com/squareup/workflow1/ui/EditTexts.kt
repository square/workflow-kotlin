package com.squareup.workflow1.ui

import android.text.Editable
import android.text.Selection.getSelectionEnd
import android.text.Selection.getSelectionStart
import android.text.TextWatcher
import android.widget.EditText

/**
 * Helper for setting the text value of an [EditText] without disrupting the IME connection, or
 * firing change listeners registered via [setTextChangedListener] if the new text is actually
 * different than the old text.
 *
 * If [text] contains a selection, then the selection of this [EditText] is updated to it.
 *
 * Intended to be used by [LayoutRunner]s for updating [EditText]s from workflow renderings.
 */
@WorkflowUiExperimentalApi
fun EditText.updateText(text: CharSequence) {
  pauseTextChangedEventsRunning {
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
}

/**
 * Helper for setting a simple function as a callback to be invoked whenever an [EditText] text
 * value changes. Simpler than manually invoking [EditText.removeTextChangedListener] and
 * [EditText.addTextChangedListener] and implementing a whole [TextWatcher] manually.
 *
 * If [listener] is not null, it will be invoked any time the text changes either due to the OS/user
 * (e.g. IME connection, soft keyboard, etc.), or programmatically (i.e. `setText`), _except_ by
 * calls to [updateText], which _will not_ fire this listener. It will also not fire if the text
 * is technically changed, but to the same value (e.g. `setText("foo"); setText("foo")` will only
 * fire the listener at most once).
 *
 * Intended to be used by [LayoutRunner]s for updating [EditText]s from workflow renderings.
 */
@WorkflowUiExperimentalApi
fun EditText.setTextChangedListener(listener: ((CharSequence) -> Unit)?) {
  val oldWatcher = textChangedListenerWatcher

  if (listener == null && oldWatcher != null) {
    removeTextChangedListener(oldWatcher)
    return
  }

  if (listener != null) {
    if (oldWatcher == null) {
      TextChangedListenerWatcher(this, listener).also { watcher ->
        textChangedListenerWatcher = watcher
        addTextChangedListener(watcher)
      }
    } else {
      oldWatcher.listener = listener
    }
  }
}

private val NO_SELECTION = -1..-1

private val CharSequence.selection: IntRange
  get() = getSelectionStart(this)..getSelectionEnd(this)

private var EditText.textChangedListenerWatcher
  get() = getTag(R.id.view_text_changed_listener) as? TextChangedListenerWatcher
  set(value) {
    setTag(R.id.view_text_changed_listener, value)
  }

/**
 * Invokes [block], and prevents any listener set by [setTextChangedListener] from firing until
 * it returns.
 */
private inline fun EditText.pauseTextChangedEventsRunning(block: () -> Unit) {
  val oldWatcher = textChangedListenerWatcher?.also(::removeTextChangedListener)
  block()
  oldWatcher?.also(::addTextChangedListener)
}

private class TextChangedListenerWatcher(
  editText: EditText,
  var listener: (CharSequence) -> Unit
) : TextWatcher {

  private var oldString: String = editText.text.toString()

  override fun onTextChanged(
    newText: CharSequence,
    start: Int,
    before: Int,
    count: Int
  ) {
    // Only fire the listener if the text has actually changed.
    val newString = newText.toString()
    if (oldString != newString) {
      oldString = newString
      listener(newText)
    }
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
