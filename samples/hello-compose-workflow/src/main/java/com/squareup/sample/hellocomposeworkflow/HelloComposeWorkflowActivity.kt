@file:OptIn(WorkflowExperimentalRuntime::class)

package com.squareup.sample.hellocomposeworkflow

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.collection.mutableLongListOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squareup.workflow1.SimpleLoggingWorkflowInterceptor
import com.squareup.workflow1.WorkflowExperimentalRuntime
import com.squareup.workflow1.WorkflowTracer
import com.squareup.workflow1.android.renderWorkflowIn
import com.squareup.workflow1.config.AndroidRuntimeConfigTools
import com.squareup.workflow1.ui.workflowContentView
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration.Companion.nanoseconds

class HelloComposeWorkflowActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // This ViewModel will survive configuration changes. It's instantiated
    // by the first call to viewModels(), and that original instance is returned by
    // succeeding calls.
    val model: HelloViewModel by viewModels()
    workflowContentView.take(lifecycle, model.renderings)
  }
}

class HelloViewModel(savedState: SavedStateHandle) : ViewModel() {
  val renderings: StateFlow<HelloRendering> by lazy {
    renderWorkflowIn(
      workflow = HelloComposeWorkflow,
      scope = viewModelScope,
      savedStateHandle = savedState,
      runtimeConfig = AndroidRuntimeConfigTools.getAppWorkflowRuntimeConfig(),
      interceptors = listOf(SimpleLoggingWorkflowInterceptor()),
      workflowTracer = object : WorkflowTracer {
        private val labels = mutableListOf<String>()
        private val startTimes = mutableLongListOf()

        @SuppressLint("Range")
        override fun beginSection(label: String) {
          Log.d("WorkflowTracer", "${indent(labels.size)}begin $label")
          labels += label
          // If startTimes needs to grow, do that before recording time.
          startTimes += 0L
          startTimes[startTimes.lastIndex] = System.nanoTime()
        }

        @SuppressLint("Range")
        override fun endSection() {
          val endTime = System.nanoTime()
          val startTime = startTimes.removeAt(startTimes.lastIndex)
          val duration = endTime.nanoseconds.minus(startTime.nanoseconds)
          val label = labels.removeAt(labels.lastIndex)
          Log.d("WorkflowTracer", "${indent(labels.size)}end $label ($duration)")
        }

        private fun indent(level: Int): String = buildString {
          repeat(level) {
            append(' ')
          }
        }
      }
    )
  }
}
