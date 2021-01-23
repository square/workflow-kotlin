package com.squareup.workflow1.ui

import android.view.View
import android.view.View.OnAttachStateChangeListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.Event
import androidx.lifecycle.Lifecycle.State.DESTROYED
import androidx.lifecycle.Lifecycle.State.RESUMED
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewTreeLifecycleOwner

/**
 * TODO write documentation
 */
@WorkflowUiExperimentalApi
public interface WorkflowLifecycleOwner : LifecycleOwner {

  /**
   * TODO write documentation
   */
  public fun destroyOnDetach()

  public companion object {
    /**
     * TODO write documentation
     */
    public fun installOn(view: View) {
      RealWorkflowLifecycleOwner(view).also {
        ViewTreeLifecycleOwner.set(view, it)
        view.addOnAttachStateChangeListener(it)
      }
    }

    /**
     * TODO write documentation
     */
    public fun get(view: View): WorkflowLifecycleOwner? =
      ViewTreeLifecycleOwner.get(view) as? WorkflowLifecycleOwner
  }
}

@OptIn(WorkflowUiExperimentalApi::class)
private class RealWorkflowLifecycleOwner(
  private val view: View
) : WorkflowLifecycleOwner,
  LifecycleOwner,
  OnAttachStateChangeListener,
  LifecycleEventObserver {

  private val localLifecycle = LifecycleRegistry(this)
  private var parentLifecycle: Lifecycle? = null
  private var destroyOnDetach = false

  override fun onViewAttachedToWindow(v: View) {
    val oldLifecycle = parentLifecycle
    parentLifecycle = findParentLifecycle()

    if (parentLifecycle !== oldLifecycle) {
      oldLifecycle?.removeObserver(this)
      parentLifecycle?.addObserver(this)
    }
  }

  override fun onViewDetachedFromWindow(v: View) {
    // Stay attached to the parent's lifecycle until we re-attach, since the parent could be
    // destroyed while we're detached.
    updateLifecycle()
  }

  /** Called when the [parentLifecycle] changes state. */
  override fun onStateChanged(
    source: LifecycleOwner,
    event: Event
  ) {
    updateLifecycle()
  }

  override fun destroyOnDetach() {
    if (!destroyOnDetach) {
      destroyOnDetach = true
      updateLifecycle()
    }
  }

  override fun getLifecycle(): Lifecycle = localLifecycle

  /**
   * Tries to get the parent lifecycle from the current view's parent view via
   * [ViewTreeLifecycleOwner], if that fails it looks up the context chain for a [LifecycleOwner],
   * and if that fails it just returns null.
   */
  private fun findParentLifecycle(): Lifecycle? {
    // Start at our view's parent – if we look on our own view, we'll just get this back.
    return (view.parent as? View)?.let { ViewTreeLifecycleOwner.get(it) }?.lifecycle
      ?: view.context.lifecycleOrNull()
  }

  private fun updateLifecycle() {
    val isAttached = view.isAttachedToWindow
    val parentState = parentLifecycle?.currentState
    val localState = localLifecycle.currentState

    if (localState == DESTROYED) {
      // Local destruction is a terminal state.
      return
    }

    localLifecycle.currentState = when {
      // We've been queued for destruction.
      destroyOnDetach && !isAttached -> DESTROYED
      // We may or may not be attached, but we have a parent lifecycle so we just blindly follow it.
      parentState != null -> parentState
      // We have no parent but we just attached, so just assume we're resumed.
      isAttached -> RESUMED
      // We have no parent and we're detached, so just stay where we are. We don't want to destroy
      // here since any view in the parent can detach and re-attach us at any time, and that doesn't
      // mean that we're going away.
      else -> localState
    }.also { newState ->
      if (newState == DESTROYED) {
        // We're now in a terminal DESTROY state. Be a good citizen and make sure to detach from
        // our parent.
        parentLifecycle?.removeObserver(this)
        parentLifecycle = null
      }
    }
  }
}
