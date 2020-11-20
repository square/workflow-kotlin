package com.squareup.sample.hellobackbutton

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.squareup.sample.container.SampleContainers
import com.squareup.workflow1.SimpleLoggingWorkflowInterceptor
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.WorkflowRunner
import com.squareup.workflow1.ui.modal.AlertContainer
import com.squareup.workflow1.ui.plus
import com.squareup.workflow1.ui.setContentWorkflow

@OptIn(WorkflowUiExperimentalApi::class)
private val viewRegistry =
  ViewRegistry(HelloBackButtonLayoutRunner) + SampleContainers + AlertContainer

class HelloBackButtonActivity : AppCompatActivity() {
  @OptIn(WorkflowUiExperimentalApi::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentWorkflow(
        viewRegistry,
        configure = {
          WorkflowRunner.Config(
              AreYouSureWorkflow,
              interceptors = listOf(SimpleLoggingWorkflowInterceptor())
          )
        },
        onResult = { finish() }
    )
  }
}
