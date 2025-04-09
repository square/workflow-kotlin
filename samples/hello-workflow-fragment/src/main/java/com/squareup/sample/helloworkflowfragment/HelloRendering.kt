package com.squareup.sample.helloworkflowfragment

import com.squareup.sample.helloworkflowfragment.databinding.HelloGoodbyeLayoutBinding
import com.squareup.workflow1.ui.AndroidScreen
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewFactory.Companion.fromViewBinding

data class HelloRendering(
  val message: String,
  val onClick: () -> Unit
) : AndroidScreen<HelloRendering> {
  override val viewFactory: ScreenViewFactory<HelloRendering> =
    fromViewBinding(HelloGoodbyeLayoutBinding::inflate) { rendering, _ ->
      helloMessage.text = "${rendering.message} Fragment"
      helloMessage.setOnClickListener { rendering.onClick() }
    }
}
