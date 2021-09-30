package com.squareup.workflow1.ui

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.TextView
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Helper class for working with [EditText]s from workflows.
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
 * 4. In your view code's `showRendering` method, call [control] and pass your [EditText].
 *    ```
 *    rendering.text.control(editText)
 *    ```
 *
 * If your workflow needs to access or change the current text value, get the value from [textValue].
 * If your workflow needs to react to changes, it can observe [onTextChanged] by converting it to a
 * worker.
 *
 * See `common/workflow-text/demo` for sample code.
 */
@WorkflowUiExperimentalApi
public class TextController(initialValue: String = "") {

  /**
   * Perform whatever maps or filters you want, then turn it into a worker and observe from your render
   * method.
   *
   * This flow is not exposed as a StateFlow intentionally. Doing so would encourage observing it from
   * workflows, which is not desirable since StateFlows emit immediately upon subscription, which means
   * that for a workflow runtime running N workflows that each observe M [TextController]s, the first
   * render pass would trigger NxM useless render passes.
   *
   * However, when subscribing from the view layer, we _want_ the initial value to ensure the
   * view gets initialized. We only want to drop the initial value when subscribing from
   * a workflow, which is the only intended use case for [onTextChanged]. Because the text
   * view wired up by [control], we don't actually need to expose the stateful nature of this
   * flow anywhere.
   *
   * Instead, only text _change_ events are exposed, as [onTextChanged], which is suitable for use as a
   * worker. The current value is exposed as a separate var, [textValue].
   */
  private val _textValue: MutableStateFlow<String> = MutableStateFlow(initialValue)

  /**
   * A [Flow] that emits the text value whenever it changes -- and only when it changes, the current value
   * is not provided at subscription time. Workflows can safely observe changes by
   * converting this value to a worker. (When using multiple instances, remember to provide unique
   * key values to each `asWorker` call.)
   *
   * If you can do processing that doesn't require running a `WorkflowAction` or triggering a render
   * pass, it can be done in regular Flow operators before converting to a worker.
   */
  public val onTextChanged: Flow<String> =
  // Drop the first value because the StateFlow will always immediately emit the current value on
    // subscription.
    _textValue.drop(1)

  /**
   * The current text value.
   */
  public var textValue: String
    get() = _textValue.value
    set(value) {
      _textValue.value = value
    }

  /**
   * Call this from your view code's `showRendering` method. This method is idempotent: if it has
   * already been called with a particular [EditText], and the view has not been detached since the
   * last call, it will do nothing. If a different [TextController]'s [control] is called on the same
   * [EditText], the old one will be disconnected and the new one will replace it.
   *
   * See [TextController] for more documentation.
   */
  public fun control(view: EditText) {
    // Do nothing if already subscribed on a previous update pass and the coroutine is still active.
    val registeredController =
      view.getTag(R.id.text_controller_rendering) as? TextControllerSubscription
    if (registeredController?.controller === this &&
      // This check ensures the subscription is re-started if the view was somehow detached since the
      // call, eg. in a RecyclerView.
      registeredController.subscription.isActive
    ) {
      return
    }

    // If we're replacing a controller with a different one, cancel the previous subscription.
    registeredController?.subscription?.cancel()

    view.setText(_textValue.value)
    val subscription = view.launchWhenAttached {
      _textValue
        .onEach { textValue ->
          // Only set the text if the actual text content has changed.
          if (textValue != view.text.toString()) {
            view.setText(textValue)
          }
        }
        .launchIn(this)

      view.listenForTextChangesUntilCancelled { updatedText ->
        _textValue.value = updatedText?.toString().orEmpty()
      }
    }
    view.setTag(R.id.text_controller_rendering, TextControllerSubscription(this, subscription))
  }
}

@OptIn(WorkflowUiExperimentalApi::class)
private class TextControllerSubscription(
  val controller: TextController,
  val subscription: Job
)

/**
 * Suspends the coroutine until cancelled, calling [handler] any time a text change event is fired.
 */
private suspend fun TextView.listenForTextChangesUntilCancelled(
  handler: (CharSequence?) -> Unit
): Nothing {
  suspendCancellableCoroutine<Nothing> { continuation ->
    val textWatcher = object : TextWatcher {
      override fun onTextChanged(
        s: CharSequence?,
        start: Int,
        before: Int,
        count: Int
      ) {
        handler(s)
      }

      override fun afterTextChanged(s: Editable) = Unit

      override fun beforeTextChanged(
        s: CharSequence?,
        start: Int,
        count: Int,
        after: Int
      ) = Unit
    }
    addTextChangedListener(textWatcher)
    continuation.invokeOnCancellation { removeTextChangedListener(textWatcher) }
  }
}
