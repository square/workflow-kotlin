package com.squareup.sample.stubvisibility

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.squareup.workflow1.SimpleLoggingWorkflowInterceptor
import com.squareup.workflow1.ui.WorkflowRunner
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.setContentWorkflow

@OptIn(WorkflowUiExperimentalApi::class)
class StubVisibilityActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentWorkflow() {
      WorkflowRunner.Config(
          StubVisibilityWorkflow,
          interceptors = listOf(SimpleLoggingWorkflowInterceptor())
      )
    }
  }
}
