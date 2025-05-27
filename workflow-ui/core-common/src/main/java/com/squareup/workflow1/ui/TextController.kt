package com.squareup.workflow1.ui

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop

/**
 * Helper class for keeping a workflow in sync with editable text in a UI,
 * without interfering with the user's typing.
 *
 * ## Usage
 *
 * 1. For every editable string in your state, create a property of type [TextController].
 *    ```
 *    data class State(val text: TextController = TextController())
 *    ```
 * 2. Create a matching property in your rendering type.
 *    ```
 *    data class Rendering(val text: TextController)
 *    ```
 * 3. In your `render` method, copy each [TextController] from your state to your rendering:
 *    ```
 *    return Rendering(state.text)
 *    ```
 * 4. In your view code's `showRendering` method, call the appropriate extension
 *    function for your UI platform, e.g.:
 *
 *    - `control()` for an Android EditText view
 *    - `asMutableTextFieldValueState()` from an Android `@Composable` function
 *
 * If your workflow needs to access or change the current text value, get the value from [textValue].
 * If your workflow needs to react to changes, it can observe [onTextChanged] by converting it to a
 * worker.
 */
public interface TextController {

  /**
   * A [Flow] that emits the text value whenever it changes -- and only when it changes, the current value
   * is not provided at subscription time. Workflows can safely observe changes by
   * converting this value to a worker. (When using multiple instances, remember to provide unique
   * key values to each `asWorker` call.)
   *
   * If you can do processing that doesn't require running a `WorkflowAction` or triggering a render
   * pass, it can be done in regular Flow operators before converting to a worker.
   */
  public val onTextChanged: Flow<String>

  /**
   * The current text value.
   */
  public var textValue: String
}

/**
 * Create instance for default implementation of [TextController].
 */
public fun TextController(initialValue: String = ""): TextController {
  return TextControllerImpl(initialValue)
}

/**
 * Default implementation of [TextController].
 */
private class TextControllerImpl(initialValue: String) : TextController {

  /**
   * This flow is not exposed as a StateFlow intentionally. Doing so would encourage observing it from
   * workflows, which is not desirable since StateFlows emit immediately upon subscription, which means
   * that for a workflow runtime running N workflows that each observe M [TextController]s, the first
   * render pass would trigger NxM useless render passes.
   *
   * Instead, only text _change_ events are exposed, as [onTextChanged], which is suitable for use as a
   * worker. The current value is exposed as a separate var, [textValue].
   *
   * Subscriptions from the view layer that need the initial value can call [textValue]
   * to prime the pump manually.
   */
  private val _textValue: MutableStateFlow<String> = MutableStateFlow(initialValue)

  override val onTextChanged: Flow<String> = _textValue.drop(1)

  override var textValue: String
    get() = _textValue.value
    set(value) {
      _textValue.value = value
    }
}
