package com.squareup.sample.helloworkflowfragment

import com.squareup.sample.helloworkflowfragment.databinding.HelloGoodbyeLayoutBinding
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.LayoutRunner
import com.squareup.workflow1.ui.ViewFactory

@OptIn(WorkflowUiExperimentalApi::class)
val HelloFragmentViewFactory: ViewFactory<HelloWorkflow.Rendering> =
  LayoutRunner.bind(HelloGoodbyeLayoutBinding::inflate) { rendering, _ ->
    helloMessage.text = rendering.message + " Fragment!"
    helloMessage.setOnClickListener { rendering.onClick() }
  }
