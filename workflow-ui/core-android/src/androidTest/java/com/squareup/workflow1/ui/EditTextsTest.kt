package com.squareup.workflow1.ui

import android.text.Selection
import android.text.SpannableStringBuilder
import android.widget.EditText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@Suppress("DEPRECATION")
@OptIn(WorkflowUiExperimentalApi::class)
@RunWith(AndroidJUnit4::class)
internal class EditTextsTest {

  private val instrumentation = InstrumentationRegistry.getInstrumentation()
  private val editText = EditText(instrumentation.context)

  @Test fun updateText_setsTextValue() {
    assertThat(editText.text.toString()).isEqualTo("")

    editText.updateText("h")
    assertThat(editText.text.toString()).isEqualTo("h")

    editText.updateText("hello")
    assertThat(editText.text.toString()).isEqualTo("hello")
  }

  @Test fun updateText_setsSelectionWhenSpecified() {
    editText.setText("hello")
    assertThat(editText.selectionStart).isEqualTo(0)
    assertThat(editText.selectionEnd).isEqualTo(0)

    val newText = SpannableStringBuilder("hello")
        .also { Selection.setSelection(it, 1, 3) }
    editText.updateText(newText)
    assertThat(editText.text.toString()).isEqualTo("hello")
    assertThat(editText.selectionStart).isEqualTo(1)
    assertThat(editText.selectionEnd).isEqualTo(3)
  }

  @Test fun updateText_preservesSelectionWhenNotSpecified() {
    editText.setText("hello")
    editText.setSelection(1, 3)

    editText.updateText("world")
    assertThat(editText.text.toString()).isEqualTo("world")
    assertThat(editText.selectionStart).isEqualTo(1)
    assertThat(editText.selectionEnd).isEqualTo(3)
  }

  @Test fun setTextChangedListener_doesntFireImmediately() {
    var fired = false

    editText.setText("hello")
    editText.setTextChangedListener { fired = true }

    assertThat(fired).isFalse()
  }

  @Test fun setTextChangedListener_doesntFireWhenTextChangedToInitialValue() {
    var fired = false

    editText.setTextChangedListener { fired = true }
    editText.setText("")

    assertThat(fired).isFalse()
  }

  @Test fun setTextChangedListener_doesntFireWhenTextChangedToCurrentValue() {
    var fired = false
    editText.setText("hello")

    editText.setTextChangedListener { fired = true }
    editText.setText("hello")

    assertThat(fired).isFalse()
  }

  @Test fun setTextChangedListener_handlesTextChanges() {
    val changes = mutableListOf<String>()
    editText.setTextChangedListener { changes += it.toString() }

    editText.setText("foo")
    assertThat(changes).containsExactly("foo")

    editText.text!!.append("bar")
    assertThat(changes).containsExactly("foo", "foobar")
  }

  @Test fun setTextChangedListener_replacesPreviousListener() {
    val changes = mutableListOf<String>()

    editText.setTextChangedListener { fail("Expected original listener not to be called.") }
    editText.setTextChangedListener { changes += it.toString() }

    editText.setText("foo")
    assertThat(changes).containsExactly("foo")
  }

  @Test fun setTextChangedListener_clearedWhenNull() {
    editText.setTextChangedListener { fail("Expected original listener not to be called.") }
    editText.setTextChangedListener(null)

    editText.setText("foo")
  }

  @Test fun updateText_doesntTriggerTextChangedListener() {
    editText.setTextChangedListener { fail("Expected updateText not to trigger listener") }

    editText.updateText("foo")
  }

  @Test fun updateText_doesntTriggerInfiniteLoopInListener() {
    editText.setTextChangedListener { editText.updateText(it) }

    editText.setText("foo")

    assertThat(editText.text.toString()).isEqualTo("foo")
  }

  @Test fun setTextChangedListener_allowsMutatingText() {
    editText.setTextChangedListener {
      editText.updateText("update: $it")
      editText.setTextChangedListener(null)
    }

    editText.setText("foo")

    assertThat(editText.text.toString()).isEqualTo("update: foo")
  }
}
