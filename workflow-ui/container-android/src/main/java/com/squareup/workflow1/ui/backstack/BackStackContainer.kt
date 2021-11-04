package com.squareup.workflow1.ui.backstack

import com.squareup.workflow1.ui.DecorativeViewFactory
import com.squareup.workflow1.ui.ViewFactory
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

@Deprecated("Use com.squareup.workflow1.ui.container.BackStackContainer")
@WorkflowUiExperimentalApi
public class BackStackContainer {
  public companion object : ViewFactory<BackStackScreen<*>>
  by DecorativeViewFactory(BackStackScreen::class, { legacy -> legacy.asNonLegacy() })
}
