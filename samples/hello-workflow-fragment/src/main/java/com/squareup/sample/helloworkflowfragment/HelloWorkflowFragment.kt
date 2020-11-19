package com.squareup.sample.helloworkflowfragment

import com.squareup.workflow1.SimpleLoggingWorkflowInterceptor
import com.squareup.workflow1.ui.WorkflowFragment
import com.squareup.workflow1.ui.WorkflowRunner
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

@OptIn(WorkflowUiExperimentalApi::class)
class HelloWorkflowFragment : WorkflowFragment<Unit, Nothing>() {
  override fun onCreateWorkflow(): WorkflowRunner.Config<Unit, Nothing> {
    return WorkflowRunner.Config(
        HelloWorkflow, interceptors = listOf(SimpleLoggingWorkflowInterceptor())
    )
  }
}
