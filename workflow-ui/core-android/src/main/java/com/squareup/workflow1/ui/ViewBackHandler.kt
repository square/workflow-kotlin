package com.squareup.workflow1.ui

import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.ViewTreeLifecycleOwner
import com.squareup.workflow1.ui.androidx.WorkflowAndroidXSupport.onBackPressedDispatcherOwnerOrNull

/**
 * A function to be called if the device back button is pressed while this
 * view is active, as determined by its [ViewTreeLifecycleOwner], via
 * an [OnBackPressedCallback]. On succeeding calls, the previously created
 * [OnBackPressedCallback] will be updated, and will maintain its position
 * in the [OnBackPressedDispatcher][androidx.activity.OnBackPressedDispatcher]
 * priority queue.
 *
 * @param enabled updates the [OnBackPressedCallback.isEnabled] value
 *
 * @param onBack the function to run from [OnBackPressedCallback.handleOnBackPressed]
 */
@WorkflowUiExperimentalApi
public fun View.setBackHandler(
  enabled: Boolean = true,
  onBack: () -> Unit
) {
  val callback = onBackPressedCallbackOrNull ?: MutableOnBackPressedCallback().apply {
    onBackPressedCallbackOrNull = this

    val dispatcher = requireNotNull(onBackPressedDispatcherOwnerOrNull()?.onBackPressedDispatcher) {
      "Unable to find a onBackPressedDispatcherOwner for ${this@setBackHandler}."
    }
    val lifecycleOwner = requireNotNull(ViewTreeLifecycleOwner.get(this@setBackHandler)) {
      "Unable to find a ViewTreeLifecycleOwner for ${this@setBackHandler}."
    }

    dispatcher.addCallback(lifecycleOwner, this)
  }
  callback.isEnabled = enabled
  callback.handler = onBack
}

/**
 * Wrapper for the two arg variant of [setBackHandler], a convenience for the
 * common pattern of using a nullable function as the back handler to indicate
 * that back handling should be disabled.
 *
 * @param onBack the handler function to run when the device back button is tapped /
 * back gesture is made. If null, the relevant [OnBackPressedCallback] will be disabled,
 * but it will still exist -- this [View]'s priority in the
 * [OnBackPressedDispatcher][androidx.activity.OnBackPressedDispatcher] queue
 * will not change, should a non-null handler be provided by a later call.
 */
@WorkflowUiExperimentalApi
public fun View.setBackHandler(onBack: (() -> Unit)?) {
  onBack?.let { setBackHandler(enabled = true, it) }
    ?: setBackHandler(enabled = false) {}
}

@WorkflowUiExperimentalApi
private var View.onBackPressedCallbackOrNull: MutableOnBackPressedCallback?
  get() = getTag(R.id.view_back_handler) as MutableOnBackPressedCallback?
  set(value) {
    setTag(R.id.view_back_handler, value)
  }

@WorkflowUiExperimentalApi
private class MutableOnBackPressedCallback : OnBackPressedCallback(false) {
  var handler: () -> Unit = {}

  override fun handleOnBackPressed() {
    handler()
  }
}
