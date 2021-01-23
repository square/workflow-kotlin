package com.squareup.workflow1.ui.viewmodel

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.stateful
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * Creates a workflow that will emit a rendering that wraps an arbitrary rendering type and provides
 * a [ViewModelStore] to the view children of the wrapped rendering's view. Don't forget to add
 * [ViewModelHost.Factory] to your `ViewRegistry`.
 */
@WorkflowUiExperimentalApi
public fun <R : Any> viewModelHostWorkflow(): Workflow<R, Nothing, ViewModelHost<R>> =
  Workflow.stateful(
    initialState = { ViewModelStoreHolder() },
    render = { props, state -> ViewModelHost(props, state) }
  )

private class ViewModelStoreHolder : ViewModelStoreOwner {
  private val store = ViewModelStore()
  override fun getViewModelStore(): ViewModelStore = store
}
