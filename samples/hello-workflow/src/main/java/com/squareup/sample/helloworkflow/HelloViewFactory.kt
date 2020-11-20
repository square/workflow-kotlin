package com.squareup.sample.helloworkflow

import com.squareup.sample.helloworkflow.HelloWorkflow.Rendering
import com.squareup.sample.helloworkflow.databinding.HelloGoodbyeLayoutBinding
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.LayoutRunner
import com.squareup.workflow1.ui.ViewFactory

@OptIn(WorkflowUiExperimentalApi::class)
val HelloViewFactory: ViewFactory<Rendering> =
  LayoutRunner.bind(HelloGoodbyeLayoutBinding::inflate) { rendering, _ ->
    helloMessage.text = rendering.message
    helloMessage.setOnClickListener { rendering.onClick() }
  }
