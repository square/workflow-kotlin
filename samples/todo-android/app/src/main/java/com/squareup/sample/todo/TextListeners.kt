package com.squareup.sample.todo

import android.content.Context.INPUT_METHOD_SERVICE
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.TextView

/**
 * Turns [TextView.addTextChangedListener] into a regular idempotent event handler.
 */
fun TextView.setTextChangedListener(listener: ((String) -> Unit)) {
  getTag(R.id.text_changed_listener)?.let { oldListener ->
    removeTextChangedListener(oldListener as TextWatcher)
  }

  val newListener = object : TextWatcher {
    private lateinit var oldText: String
    override fun afterTextChanged(s: Editable?) {
      if (s.toString() != oldText) listener(s.toString())
    }

    override fun beforeTextChanged(
      s: CharSequence?,
      start: Int,
      count: Int,
      after: Int
    ) {
      oldText = s.toString()
    }

    override fun onTextChanged(
      s: CharSequence?,
      start: Int,
      before: Int,
      count: Int
    ) {
    }
  }
  addTextChangedListener(newListener)
  setTag(R.id.text_changed_listener, newListener)
}

fun View.showSoftKeyboard() {
  val inputMethodManager = context.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
  inputMethodManager.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
}
