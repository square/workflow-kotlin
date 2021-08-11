package com.squareup.sample.helloworkflow

import com.squareup.sample.helloworkflow.databinding.HelloGoodbyeLayoutBinding
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.StatelessWorkflow
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.ui.AndroidViewRendering
import com.squareup.workflow1.ui.LayoutRunner
import com.squareup.workflow1.ui.ViewFactory
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

@OptIn(WorkflowUiExperimentalApi::class)
data class HelloAndroidRendering(
  override val message: String,
  override val onClick: () -> Unit
) : AndroidViewRendering<HelloAndroidRendering>, HelloRendering {
  override val viewFactory: ViewFactory<HelloAndroidRendering> =
    LayoutRunner.bind(HelloGoodbyeLayoutBinding::inflate) { r, _ ->
      helloMessage.text = r.message
      helloMessage.setOnClickListener { r.onClick() }
    }
}
