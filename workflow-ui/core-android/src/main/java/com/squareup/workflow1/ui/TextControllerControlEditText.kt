package com.squareup.workflow1.ui

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.TextView
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Call this from your view code's `showRendering` method. This method is idempotent: if it has
 * already been called with a particular [EditText], and the view has not been detached since the
 * last call, it will do nothing. If a different [TextController]'s [control] is called on the same
 * [EditText], the old one will be disconnected and the new one will replace it.
 *
 * See [TextController] for more documentation.
 */
@WorkflowUiExperimentalApi
public fun TextController.control(view: EditText) {
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

  view.setText(textValue)
  val subscription = view.launchWhenAttached {
    onTextChanged
      .onEach { textValue ->
        // Only set the text if the actual text content has changed.
        if (textValue != view.text.toString()) {
          view.setText(textValue)
        }
      }
      .launchIn(this)

    view.listenForTextChangesUntilCancelled { updatedText ->
      textValue = updatedText?.toString().orEmpty()
    }
  }
  view.setTag(R.id.text_controller_rendering, TextControllerSubscription(this, subscription))
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
