package com.squareup.workflow1.ui.viewmodel

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.ViewTreeViewModelStoreOwner
import com.squareup.workflow1.ui.DecorativeViewFactory
import com.squareup.workflow1.ui.ViewFactory
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * Opaque rendering type that wraps another rendering and will ensure that the view hierarchy
 * created for the wrapped rendering has access to a [ViewModelStore] via
 * [ViewTreeViewModelStoreOwner][androidx.lifecycle.ViewTreeViewModelStoreOwner].
 *
 * Do not create instances of this type yourself, instead render a [viewModelHostWorkflow] and pass
 * your rendering to be wrapped.
 *
 * To use this, make sure to add [Factory] to your `ViewRegistry`.
 */
public class ViewModelHost<out R : Any>(
  private val wrapped: R,
  private val viewModelStoreOwner: ViewModelStoreOwner
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ViewModelHost<*>

    if (wrapped != other.wrapped) return false
    if (viewModelStoreOwner !== other.viewModelStoreOwner) return false

    return true
  }

  override fun hashCode(): Int {
    var result = wrapped.hashCode()
    result = 31 * result + viewModelStoreOwner.hashCode()
    return result
  }

  /**
   * A [ViewFactory] that doesn't have a view of its own, but provides a [ViewModelStore] hosted by
   * a [viewModelHostWorkflow] to the children of the view created to show the [ViewModelHost]'s
   * wrapped rendering.
   */
  @OptIn(WorkflowUiExperimentalApi::class)
  public companion object Factory : ViewFactory<ViewModelHost<*>>
  by DecorativeViewFactory(
    ViewModelHost::class,
    map = { named -> named.wrapped },
    initView = { host, view ->
      ViewTreeViewModelStoreOwner.set(view, host.viewModelStoreOwner)
    }
  )
}
