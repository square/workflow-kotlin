package com.squareup.sample.hellobackbutton

import android.view.View
import android.widget.TextView
import com.squareup.workflow1.ui.AndroidScreen
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewRunner
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.backPressedHandler

@OptIn(WorkflowUiExperimentalApi::class)
data class HelloBackButtonScreen(
  val message: String,
  val onClick: () -> Unit,
  val onBackPressed: (() -> Unit)?
) : AndroidScreen<HelloBackButtonScreen> {
  override val viewFactory: ScreenViewFactory<HelloBackButtonScreen> = ScreenViewRunner.bind(
    R.layout.hello_back_button_layout, ::HelloBackButtonLayoutRunner
  )
}

@OptIn(WorkflowUiExperimentalApi::class)
private class HelloBackButtonLayoutRunner(view: View) : ScreenViewRunner<HelloBackButtonScreen> {
  private val messageView: TextView = view.findViewById(R.id.hello_message)

  override fun showRendering(
    rendering: HelloBackButtonScreen,
    viewEnvironment: ViewEnvironment
  ) {
    messageView.text = rendering.message
    messageView.setOnClickListener { rendering.onClick() }
    messageView.backPressedHandler = rendering.onBackPressed
  }
}
