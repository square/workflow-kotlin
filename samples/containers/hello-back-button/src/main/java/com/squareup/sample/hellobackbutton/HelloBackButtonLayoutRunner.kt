package com.squareup.sample.hellobackbutton

import android.view.View
import android.widget.TextView
import com.squareup.sample.hellobackbutton.HelloBackButtonWorkflow.Rendering
import com.squareup.sample.hellobackbutton.R.id
import com.squareup.sample.hellobackbutton.R.layout
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.LayoutRunner
import com.squareup.workflow1.ui.LayoutRunner.Companion.bind
import com.squareup.workflow1.ui.ViewFactory
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.backPressedHandler

@OptIn(WorkflowUiExperimentalApi::class)
class HelloBackButtonLayoutRunner(view: View) : LayoutRunner<Rendering> {
  private val messageView: TextView = view.findViewById(id.hello_message)

  override fun showRendering(
    rendering: Rendering,
    viewEnvironment: ViewEnvironment
  ) {
    messageView.text = rendering.message
    messageView.setOnClickListener { rendering.onClick() }
    messageView.backPressedHandler = rendering.onBackPressed
  }

  companion object : ViewFactory<Rendering> by bind(
      layout.hello_back_button_layout, ::HelloBackButtonLayoutRunner
  )
}
