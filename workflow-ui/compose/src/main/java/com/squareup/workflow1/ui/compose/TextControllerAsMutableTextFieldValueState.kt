package com.squareup.workflow1.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.squareup.workflow1.ui.TextController
import kotlinx.coroutines.launch

/**
 * A wrapper extension for [com.squareup.workflow1.ui.compose.asMutableState] that returns
 * [TextFieldValue]. This makes it easy to use it with `MarketTextField` since `MarketTextField`
 * expects [TextFieldValue].
 *
 * @param selectionStart The starting index of the selection.
 * @param selectionEnd The ending index of the selection.
 *
 * If [selectionStart] equals [selectionEnd] then nothing is selected, and the cursor is placed at
 * [selectionStart]. By default, the cursor will be placed at the end of the text.
 *
 * Usage:
 *
 *    var fooText by fooTextController.asMutableTextFieldValueState()
 *    BasicTextField(
 *      value = fooText,
 *      onValueChange = { fooText = it },
 *    )
 *
 */
@Composable
public fun TextController.asMutableTextFieldValueState(
  selectionStart: Int = textValue.length,
  selectionEnd: Int = selectionStart,
): MutableState<TextFieldValue> {
  val textFieldValue = remember(this) {
    val actualStart = selectionStart.coerceIn(0, textValue.length)
    val actualEnd = selectionEnd.coerceIn(actualStart, textValue.length)
    mutableStateOf(
      TextFieldValue(
        text = textValue,
        // We need to set the selection manually when creating  new `TextFieldValue` whenever
        // `TextController` changes because the text inside may not be empty.
        selection = TextRange(actualStart, actualEnd),
      )
    )
  }

  LaunchedEffect(this) {
    launch {
      // This is to address the case when value of `TextController` is updated within the workflow.
      // By subscribing directly to `onTextChanged` we can use this to also update the textFieldValue.
      onTextChanged
        .collect { newText ->
          // Only update the `textFieldValue` if the new text is different from the current text.
          // This ensures the selection is maintained when the text is updated from the UI side,
          // and is only reset when the text is changed via `TextController`.
          if (textFieldValue.value.text != newText) {
            textFieldValue.value = TextFieldValue(
              text = newText,
              selection = TextRange(newText.length),
            )
          }
        }
    }

    // Update this `TextController`'s text whenever the `textFieldValue` changes.
    snapshotFlow { textFieldValue.value }
      .collect { newText ->
        textValue = newText.text
      }
  }

  return textFieldValue
}
