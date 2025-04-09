package com.squareup.workflow1.ui.navigation

import com.squareup.workflow1.ui.ViewEnvironmentKey

/**
 * True in views managed by [BodyAndOverlaysScreen] when their events are being blocked
 * by a [ModalOverlay], giving covered views a signal that they should ignore events.
 * This is necessary so that we can ignore events that happen in the time between calls
 * to `Dialog.show()` and the actual appearance of the Dialog window.
 *
 * https://stackoverflow.com/questions/2886407/dealing-with-rapid-tapping-on-buttons
 */
public object CoveredByModal : ViewEnvironmentKey<Boolean>() {
  override val default: Boolean = false
}
