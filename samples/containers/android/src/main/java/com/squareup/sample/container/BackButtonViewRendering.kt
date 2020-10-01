package com.squareup.sample.container

import com.squareup.workflow1.ui.ViewRendering
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * Adds optional back button handling to a [wrapped] rendering, possibly overriding that
 * the wrapped rendering's own back button handler.
 *
 * @param override If `true`, [onBackPressed] is set as the
 * [backPressedHandler][android.view.View.backPressedHandler] after
 * the [wrapped] rendering's view is built / updated. If false, ours
 * is set afterward, to allow the wrapped rendering to take precedence.
 * Defaults to `false`.
 *
 * @param onBackPressed The function to fire when the device back button
 * is pressed, or null to set no handler. Defaults to `null`.
 */
@WorkflowUiExperimentalApi
data class BackButtonViewRendering<W : ViewRendering>(
  val wrapped: W,
  val override: Boolean = false,
  val onBackPressed: (() -> Unit)? = null
) : ViewRendering
