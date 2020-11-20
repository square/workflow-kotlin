package com.squareup.sample.dungeon

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.squareup.workflow1.diagnostic.tracing.TracingWorkflowInterceptor
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.WorkflowRunner
import com.squareup.workflow1.ui.setContentWorkflow

class MainActivity : AppCompatActivity() {

  @OptIn(WorkflowUiExperimentalApi::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Ignore config changes for now.
    val component = Component(applicationContext)

    val traceFile = getExternalFilesDir(null)?.resolve("workflow-trace-dungeon.json")!!
    setContentWorkflow(component.viewRegistry) {
      WorkflowRunner.Config(
          workflow = component.timeMachineWorkflow,
          props = "simple_maze.txt",
          interceptors = listOf(TracingWorkflowInterceptor(traceFile))
      )
    }
  }
}
