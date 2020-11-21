package com.squareup.sample.ravenapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.squareup.sample.container.SampleContainers
import com.squareup.sample.poetry.PoemWorkflow
import com.squareup.sample.poetry.model.Raven
import com.squareup.workflow1.SimpleLoggingWorkflowInterceptor
import com.squareup.workflow1.ui.WorkflowRunner
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.backstack.BackStackContainer
import com.squareup.workflow1.ui.plus
import com.squareup.workflow1.ui.setContentWorkflow
import timber.log.Timber

@OptIn(WorkflowUiExperimentalApi::class)
private val viewRegistry = SampleContainers + BackStackContainer

class RavenActivity : AppCompatActivity() {
  @OptIn(WorkflowUiExperimentalApi::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentWorkflow(
      registry = viewRegistry,
      configure = {
        WorkflowRunner.Config(
          PoemWorkflow,
          Raven,
          interceptors = listOf(object : SimpleLoggingWorkflowInterceptor() {
            override fun log(text: String) = Timber.v(text)
          })
        )
      },
      onResult = { finish() }
    )
  }

  companion object {
    init {
      Timber.plant(Timber.DebugTree())
    }
  }
}
