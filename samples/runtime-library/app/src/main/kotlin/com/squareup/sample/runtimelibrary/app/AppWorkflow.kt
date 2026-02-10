package com.squareup.sample.runtimelibrary.app

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import com.squareup.workflow1.StatelessWorkflow
import com.squareup.workflow1.ui.compose.ComposeScreen

data class AppRendering(val message: String) : ComposeScreen {
  @Composable override fun Content() {
    BasicText(message)
  }
}

object AppWorkflow : StatelessWorkflow<Unit, Nothing, AppRendering>() {
  override fun render(
    renderProps: Unit,
    context: RenderContext<Unit, Nothing>
  ): AppRendering = AppRendering("Hello")
}
