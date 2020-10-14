package com.squareup.workflow1.ui

import com.squareup.workflow1.ui.BackStackConfig.First
import com.squareup.workflow1.ui.BackStackConfig.Other

/**
 * Informs views whether they're children of a [BackStackView],
 * and if so whether they're the [first frame][First] or [not][Other].
 */
@WorkflowUiExperimentalApi
enum class BackStackConfig {
  /**
   * There is no [BackStackView] above here.
   */
  None,

  /**
   * This rendering is the first frame in a [BackStackViewRendering].
   * Useful as a hint to disable "go back" behavior, or replace it with "go up" behavior.
   */
  First,

  /**
   * This rendering is in a [BackStackViewRendering] but is not the first frame.
   * Useful as a hint to enable "go back" behavior.
   */
  Other;

  companion object : ViewEnvironmentKey<BackStackConfig>(BackStackConfig::class) {
    override val default = None
  }
}
