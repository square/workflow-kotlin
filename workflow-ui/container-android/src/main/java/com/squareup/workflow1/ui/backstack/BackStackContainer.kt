@file:Suppress("DEPRECATION")

package com.squareup.workflow1.ui.backstack

import com.squareup.workflow1.ui.DecorativeViewFactory
import com.squareup.workflow1.ui.ViewFactory
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * BackStackContainer has been promoted to the core workflow-ui modules,
 * and is now built into [ViewRegistry][com.squareup.workflow1.ui.ViewRegistry] by default.
 *
 * This stub has been left in place to preserve the name of the legacy [ViewFactory],
 * to ease conversion.
 */
@Deprecated("Use com.squareup.workflow1.ui.container.BackStackContainer")
@WorkflowUiExperimentalApi
public class BackStackContainer {
  public companion object : ViewFactory<BackStackScreen<*>>
  by DecorativeViewFactory(BackStackScreen::class, { legacy -> legacy.asNonLegacy() })
}
