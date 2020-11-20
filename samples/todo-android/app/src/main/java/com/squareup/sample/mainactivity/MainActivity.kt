package com.squareup.sample.mainactivity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.squareup.sample.container.overviewdetail.OverviewDetailContainer
import com.squareup.sample.todo.TodoListsAppWorkflow
import com.squareup.workflow1.SimpleLoggingWorkflowInterceptor
import com.squareup.workflow1.diagnostic.tracing.TracingWorkflowInterceptor
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.WorkflowRunner
import com.squareup.workflow1.ui.backstack.BackStackContainer
import com.squareup.workflow1.ui.setContentWorkflow

@OptIn(WorkflowUiExperimentalApi::class)
class MainActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val traceFile = getExternalFilesDir(null)?.resolve("workflow-trace-todo.json")!!

    setContentWorkflow(viewRegistry) {
      WorkflowRunner.Config(
          TodoListsAppWorkflow,
          interceptors = listOf(
              SimpleLoggingWorkflowInterceptor(),
              TracingWorkflowInterceptor(traceFile)
          )
      )
    }
  }

  private companion object {
    val viewRegistry =
      ViewRegistry(
          TodoEditorLayoutRunner,
          TodoListsViewFactory,
          OverviewDetailContainer,
          BackStackContainer
      )
  }
}
