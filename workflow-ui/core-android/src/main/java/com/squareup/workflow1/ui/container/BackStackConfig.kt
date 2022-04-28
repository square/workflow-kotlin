package com.squareup.workflow1.ui.container

import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewEnvironmentKey
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.container.BackStackConfig.First
import com.squareup.workflow1.ui.container.BackStackConfig.Other

/**
 * Informs views whether they're children of a [BackStackContainer],
 * and if so whether they're the [first frame][First] or [not][Other].
 */
@WorkflowUiExperimentalApi
public enum class BackStackConfig {
  /**
   * There is no [BackStackContainer] above here.
   */
  None,

  /**
   * This rendering is the first frame in a [BackStackScreen].
   * Useful as a hint to disable "go back" behavior, or replace it with "go up" behavior.
   */
  First,

  /**
   * This rendering is in a [BackStackScreen] but is not the first frame.
   * Useful as a hint to enable "go back" behavior.
   */
  Other;

  public companion object : ViewEnvironmentKey<BackStackConfig>(BackStackConfig::class) {
    override val default: BackStackConfig = None
  }
}

@WorkflowUiExperimentalApi
public operator fun ViewEnvironment.plus(config: BackStackConfig): ViewEnvironment =
  this + (BackStackConfig to config)
