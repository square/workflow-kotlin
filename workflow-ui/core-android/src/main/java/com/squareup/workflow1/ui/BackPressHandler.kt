package com.squareup.workflow1.ui

import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.View.OnAttachStateChangeListener
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewTreeLifecycleOwner

/**
 * A function passed to [View.backPressedHandler], to be called if the back
 * button is pressed while that view is attached to a window.
 */
@WorkflowUiExperimentalApi
public typealias BackPressHandler = () -> Unit

/**
 * A function to be called if the device back button is pressed while this
 * view is attached to a window.
 *
 * Implemented via [OnBackPressedDispatcher][androidx.activity.OnBackPressedDispatcher],
 * making this a last-registered-first-served mechanism.
 */
@WorkflowUiExperimentalApi
public var View.backPressedHandler: BackPressHandler?
  get() = handlerWrapperOrNull?.handler
  set(value) {
    handlerWrapperOrNull?.stop()

    val wrapper = value?.let {
      HandleBackPressWhenAttached(this, it).apply { start() }
    }
    setTag(R.id.view_back_handler, wrapper)
  }

@WorkflowUiExperimentalApi
private val View.handlerWrapperOrNull
  get() = getTag(R.id.view_back_handler) as HandleBackPressWhenAttached?

/**
 * Uses the [androidx.activity.OnBackPressedDispatcher] to associate a [BackPressHandler]
 * with a [View].
 *
 * - Registers [handler] on [start]
 * - Enables and disables it as [view] is attached to or detached from its window
 * - De-registers it on [stop], or when its [lifecycle][ViewTreeLifecycleOwner] is destroyed
 */
@WorkflowUiExperimentalApi
private class HandleBackPressWhenAttached(
  private val view: View,
  val handler: BackPressHandler
) : OnAttachStateChangeListener, DefaultLifecycleObserver {
  private val onBackPressedCallback = object : OnBackPressedCallback(false) {
    override fun handleOnBackPressed() {
      handler.invoke()
    }
  }

  fun start() {
    view.context.onBackPressedDispatcherOwnerOrNull()
      ?.let { owner ->
        owner.onBackPressedDispatcher.addCallback(owner, onBackPressedCallback)
        view.addOnAttachStateChangeListener(this)
        if (view.isAttachedToWindow) onViewAttachedToWindow(view)

        // We enable the handler only while its view is attached to a window.
        // This ensures that a temporarily removed view (e.g. for caching)
        // does not participate in back button handling.
        ViewTreeLifecycleOwner.get(view)?.lifecycle?.addObserver(this)
      }
  }

  fun stop() {
    onBackPressedCallback.remove()
    view.removeOnAttachStateChangeListener(this)
    ViewTreeLifecycleOwner.get(view)?.lifecycle?.removeObserver(this)
  }

  override fun onViewAttachedToWindow(attachedView: View) {
    require(view === attachedView)
    onBackPressedCallback.isEnabled = true
  }

  override fun onViewDetachedFromWindow(detachedView: View) {
    require(view === detachedView)
    onBackPressedCallback.isEnabled = false
  }

  override fun onDestroy(owner: LifecycleOwner) {
    stop()
  }
}

@WorkflowUiExperimentalApi
public tailrec fun Context.onBackPressedDispatcherOwnerOrNull(): OnBackPressedDispatcherOwner? =
  when (this) {
    is OnBackPressedDispatcherOwner -> this
    else -> (this as? ContextWrapper)?.baseContext?.onBackPressedDispatcherOwnerOrNull()
  }
