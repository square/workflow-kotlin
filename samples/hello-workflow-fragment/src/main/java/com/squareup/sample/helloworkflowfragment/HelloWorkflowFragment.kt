@file:OptIn(WorkflowExperimentalRuntime::class)

package com.squareup.sample.helloworkflowfragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.squareup.workflow1.WorkflowExperimentalRuntime
import com.squareup.workflow1.android.renderWorkflowIn
import com.squareup.workflow1.config.AndroidRuntimeConfigTools
import com.squareup.workflow1.ui.WorkflowLayout
import kotlinx.coroutines.flow.StateFlow

class HelloWorkflowFragment : Fragment() {
  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): WorkflowLayout {
    // This ViewModel will survive configuration changes. It's instantiated
    // by the first call to ViewModelProvider.get(), and that original instance is returned by
    // succeeding calls, until this Fragment session ends.
    val model: HelloViewModel = ViewModelProvider(this)[HelloViewModel::class.java]

    return WorkflowLayout(inflater.context).apply {
      take(lifecycle, model.renderings)
    }
  }
}

class HelloViewModel(savedState: SavedStateHandle) : ViewModel() {
  val renderings: StateFlow<HelloRendering> by lazy {
    renderWorkflowIn(
      workflow = HelloWorkflow,
      scope = viewModelScope,
      savedStateHandle = savedState,
      runtimeConfig = AndroidRuntimeConfigTools.getAppWorkflowRuntimeConfig()
    )
  }
}
