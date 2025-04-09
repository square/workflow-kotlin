package com.squareup.sample.container.overviewdetail

import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewEnvironmentKey

/**
 * [com.squareup.workflow1.ui.ViewEnvironment] value that informs views
 * whether they're children of a [OverviewDetailContainer], and if so
 * in what configuration.
 */
enum class OverviewDetailConfig {
  /**
   * There is no [OverviewDetailContainer] above here.
   */
  None,

  /**
   * Drawing on the overview side of a overview / detail split screen.
   */
  Overview,

  /**
   * Drawing on the detail side of a overview / detail split screen.
   */
  Detail,

  /**
   * Drawing in single screen configuration.
   */
  Single;

  companion object : ViewEnvironmentKey<OverviewDetailConfig>() {
    override val default = None
  }
}

operator fun ViewEnvironment.plus(config: OverviewDetailConfig): ViewEnvironment =
  this + (OverviewDetailConfig to config)
