package com.squareup.sample.hellobackbutton

import android.view.View
import android.widget.TextView
import com.squareup.workflow1.ui.AndroidViewRendering
import com.squareup.workflow1.ui.LayoutRunner
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewFactory
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.backPressedHandler

@OptIn(WorkflowUiExperimentalApi::class)
data class HelloBackButtonRendering(
  val message: String,
  val onClick: () -> Unit,
  val onBackPressed: (() -> Unit)?
) : AndroidViewRendering<HelloBackButtonRendering> {
  override val viewFactory: ViewFactory<HelloBackButtonRendering> = LayoutRunner.bind(
    R.layout.hello_back_button_layout, ::HelloBackButtonLayoutRunner
  )
}

@OptIn(WorkflowUiExperimentalApi::class)
private class HelloBackButtonLayoutRunner(view: View) : LayoutRunner<HelloBackButtonRendering> {
  private val messageView: TextView = view.findViewById(R.id.hello_message)

  override fun showRendering(
    rendering: HelloBackButtonRendering,
    viewEnvironment: ViewEnvironment
  ) {
    messageView.text = rendering.message
    messageView.setOnClickListener { rendering.onClick() }
    messageView.backPressedHandler = rendering.onBackPressed
  }
}
