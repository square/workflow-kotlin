@file:Suppress("ktlint:standard:filename")

package com.squareup.sample.hellobackbutton

import com.squareup.sample.hellobackbutton.databinding.HelloBackButtonLayoutBinding
import com.squareup.workflow1.ui.AndroidScreen
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.navigation.setBackHandler

@OptIn(WorkflowUiExperimentalApi::class)
data class HelloBackButtonScreen(
  val message: String,
  val onClick: () -> Unit,
  val onBackPressed: (() -> Unit)?
) : AndroidScreen<HelloBackButtonScreen> {
  override val viewFactory: ScreenViewFactory<HelloBackButtonScreen> =
    ScreenViewFactory.fromViewBinding(HelloBackButtonLayoutBinding::inflate) { rendering, _ ->
      helloMessage.text = rendering.message
      helloMessage.setOnClickListener { rendering.onClick() }
      helloMessage.setBackHandler(rendering.onBackPressed)
    }
}
