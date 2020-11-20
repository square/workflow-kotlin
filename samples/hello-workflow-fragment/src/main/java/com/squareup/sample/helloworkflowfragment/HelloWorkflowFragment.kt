package com.squareup.sample.helloworkflowfragment

import com.squareup.workflow1.SimpleLoggingWorkflowInterceptor
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.WorkflowFragment
import com.squareup.workflow1.ui.WorkflowRunner

@OptIn(WorkflowUiExperimentalApi::class)
class HelloWorkflowFragment : WorkflowFragment<Unit, Nothing>() {
  override val viewEnvironment = ViewEnvironment(ViewRegistry(HelloFragmentViewFactory))

  override fun onCreateWorkflow(): WorkflowRunner.Config<Unit, Nothing> {
    return WorkflowRunner.Config(
        HelloWorkflow, interceptors = listOf(SimpleLoggingWorkflowInterceptor())
    )
  }
}
