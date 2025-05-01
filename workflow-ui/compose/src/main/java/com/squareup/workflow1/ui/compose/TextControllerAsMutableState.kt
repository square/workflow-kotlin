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
 * Exposes the [textValue][TextController.textValue] of a [TextController]
 * as a remembered [MutableState], suitable for use from `@Composable`
 * functions.
 *
 * Usage:
 *
 *    var text by rendering.textController.asMutableState()
 *
 *    OutlinedTextField(
 *      label = {},
 *      placeholder = { Text("Enter some text") },
 *      value = text,
 *      onValueChange = { text = it }
 *    )
 */
@Composable public fun TextController.asMutableState(): MutableState<String> {
  // keys are set to `this` to reset the state if a different controller is passed in…
  return remember(this) { mutableStateOf(textValue) }.also { state ->
    // …and to restart the effect.
    LaunchedEffect(this) {
      // Push changes from the workflow to the state.
      launch {
        onTextChanged.collect { state.value = it }
      }
      // And the other way – push changes to the state to the workflow.
      // This won't cause an infinite loop because both MutableState and
      // MutableSnapshotFlow ignore duplicate values.
      snapshotFlow { state.value }
        .collect { textValue = it }
    }
  }
}

/**
 * Exposes the [textValue][TextController.textValue] of a [TextController]
 * as a remembered [MutableState] of [TextFieldValue], suitable for use from `@Composable`
 * functions.
 *
 * Usage:
 *
 *    ```
 *    var fooText by fooTextController.asMutableTextFieldValueState()
 *    BasicTextField(
 *      value = fooText,
 *      onValueChange = { fooText = it },
 *    )
 *    ```
 *
 * @param initialSelection The initial range of selection. If [TextRange.start] equals
 * [TextRange.end], then nothing is selected, and the cursor is placed at
 * [TextRange.start]. By default, the cursor will be placed at the end of the text.
 */
@Composable public fun TextController.asMutableTextFieldValueState(
  initialSelection: TextRange = TextRange(textValue.length),
): MutableState<TextFieldValue> {
  val textFieldValue = remember(this) {
    val actualStart = initialSelection.start.coerceIn(0, textValue.length)
    val actualEnd = initialSelection.end.coerceIn(actualStart, textValue.length)
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
