package com.squareup.workflow1.ui.container

import com.squareup.workflow1.ui.ViewEnvironmentKey
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * True in views managed by [BodyAndModalsScreen] when their events are being blocked
 * by a modal [Overlay].
 */
@WorkflowUiExperimentalApi
internal object CoveredByModal : ViewEnvironmentKey<Boolean>(type = Boolean::class) {
  override val default: Boolean = false
}
