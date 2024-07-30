package com.squareup.sample.nestedoverlays

import androidx.compose.runtime.Composable
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.Wrapper
import com.squareup.workflow1.ui.compose.ComposeScreen
import com.squareup.workflow1.ui.compose.WorkflowRendering

@OptIn(WorkflowUiExperimentalApi::class)
class ForceComposeWrapper<C : Screen>(
  override val content: C
) : ComposeScreen, Wrapper<Screen, C> {
  @Composable override fun Content() {
    WorkflowRendering(content)
  }

  override fun <D : Screen> map(transform: (C) -> D) = ForceComposeWrapper(transform(content))
}
