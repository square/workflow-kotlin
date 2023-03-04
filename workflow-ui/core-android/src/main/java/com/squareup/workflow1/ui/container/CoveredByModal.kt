package com.squareup.workflow1.ui.container

import com.squareup.workflow1.ui.ViewEnvironmentKey
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * True in views managed by [BodyAndOverlaysScreen] when their events are being blocked
 * by a [ModalOverlay], giving covered views a signal that they should ignore events.
 * This is necessary so that we can ignore events that happen in the time between calls
 * to `Dialog.show()` and the actual appearance of the Dialog window.
 *
 * https://stackoverflow.com/questions/2886407/dealing-with-rapid-tapping-on-buttons
 */
@WorkflowUiExperimentalApi
internal object CoveredByModal : ViewEnvironmentKey<Boolean>(type = Boolean::class) {
  override val default: Boolean = false
}
