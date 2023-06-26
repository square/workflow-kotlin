package com.squareup.workflow1.ui

import android.view.View
import android.view.View.OnAttachStateChangeListener
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewTreeLifecycleOwner
import com.squareup.workflow1.ui.androidx.WorkflowAndroidXSupport.onBackPressedDispatcherOwnerOrNull

/**
 * A function passed to [View.backPressedHandler], to be called if the back
 * button is pressed while that view is attached to a window.
 */
@Deprecated("Use View.backHandler()")
@WorkflowUiExperimentalApi
public typealias BackPressHandler = () -> Unit

/**
 * A function to be called if the device back button is pressed while this
 * view is attached to a window.
 *
 * Implemented via [OnBackPressedDispatcher][androidx.activity.OnBackPressedDispatcher].
 * That means that this is a last-registered-first-served mechanism, and that it is
 * compatible with Compose back button handling.
 */
@Suppress("DEPRECATION")
@WorkflowUiExperimentalApi
@Deprecated("Use setBackHandler")
public var View.backPressedHandler: BackPressHandler?
  get() = observerOrNull?.handler
  set(value) {
    observerOrNull?.stop()

    observerOrNull = value?.let {
      AttachStateAndLifecycleObserver(this, it).apply { start() }
    }
  }

@WorkflowUiExperimentalApi
private var View.observerOrNull: AttachStateAndLifecycleObserver?
  get() = getTag(R.id.view_deprecated_back_handler) as AttachStateAndLifecycleObserver?
  set(value) {
    setTag(R.id.view_deprecated_back_handler, value)
  }

/**
 * This is more complicated than one would hope because [Lifecycle] and memory leaks.
 *
 * - We need to claim our spot in the
 *   [OnBackPressedDispatcher][androidx.activity.OnBackPressedDispatcher] immediately,
 *   to ensure our [onBackPressedCallback] shadows upstream ones, and can be shadowed
 *   appropriately itself
 * - The whole point of this mechanism is to be active only while the view is active
 * - That's what [ViewTreeLifecycleOwner] is for, but we can't really find that until
 *   we're attached -- which often does not happen in the order required for registering
 *   with the dispatcher
 *
 * So, our [start] is called immediately, to get [onBackPressedCallback] listed at the right
 * spot in the dispatcher's stack. But the [onBackPressedCallback]'s enabled / disabled state
 * is tied to whether the [view] is attached or not.
 *
 * Also note that we expect to find a [ViewTreeLifecycleOwner] at attach time,
 * so that we can know when it's time to remove the [onBackPressedCallback] from
 * the dispatch stack
 * ([no memory leaks please](https://github.com/square/workflow-kotlin/issues/889)).
 *
 * Why is it okay to wait for the [ViewTreeLifecycleOwner] to be destroyed before we
 * remove [onBackPressedCallback] from the dispatcher? In normal apps that's
 * the `Activity` or a `Fragment`, which will live a very long time, but Workflow UI
 * is more controlling than that. `WorkflowViewStub` and the rest of the stock container
 * classes use `WorkflowLifecycleOwner` to provide a short lived [ViewTreeLifecycleOwner]
 * for each [View] they create, and tear it down before moving to the next one.
 *
 * None the less, as a belt-and-suspenders guard against leaking,
 * we also take care to null out the pointer from the [onBackPressedCallback] to the
 * actual [handler] while the [view] is detached. We can't be confident that the
 * [ViewTreeLifecycleOwner] we find will be a well behaved one that was put in place
 * by `WorkflowLifecycleOwner`. Who knows what adventures our clients will get up to.
 */
@WorkflowUiExperimentalApi
private class AttachStateAndLifecycleObserver(
  private val view: View,
  @Suppress("DEPRECATION") val handler: BackPressHandler
) : OnAttachStateChangeListener, DefaultLifecycleObserver {
  private val onBackPressedCallback = NullableOnBackPressedCallback()
  private var lifecycleOrNull: Lifecycle? = null

  fun start() {
    view.onBackPressedDispatcherOwnerOrNull()
      ?.let { owner ->
        owner.onBackPressedDispatcher.addCallback(owner, onBackPressedCallback)
        view.addOnAttachStateChangeListener(this)
        if (view.isAttachedToWindow) onViewAttachedToWindow(view)
      }
  }

  fun stop() {
    onBackPressedCallback.remove()
    view.removeOnAttachStateChangeListener(this)
    lifecycleOrNull?.removeObserver(this)
  }

  override fun onViewAttachedToWindow(attachedView: View) {
    require(view === attachedView)
    lifecycleOrNull?.let { lifecycle ->
      lifecycle.removeObserver(this)
      lifecycleOrNull = null
    }
    ViewTreeLifecycleOwner.get(view)?.lifecycle?.let { lifecycle ->
      lifecycleOrNull = lifecycle
      onBackPressedCallback.handlerOrNull = handler
      onBackPressedCallback.isEnabled = true
      lifecycle.addObserver(this)
    }
      ?: error(
        "Expected to find a ViewTreeLifecycleOwner to manage the " +
          "backPressedHandler ($handler) for $view"
      )
  }

  override fun onViewDetachedFromWindow(detachedView: View) {
    require(view === detachedView)
    onBackPressedCallback.isEnabled = false
    onBackPressedCallback.handlerOrNull = null
  }

  override fun onDestroy(owner: LifecycleOwner) {
    stop()
  }
}

@WorkflowUiExperimentalApi
internal class NullableOnBackPressedCallback : OnBackPressedCallback(false) {
  @Suppress("DEPRECATION")
  var handlerOrNull: BackPressHandler? = null

  override fun handleOnBackPressed() {
    handlerOrNull?.invoke()
  }
}
