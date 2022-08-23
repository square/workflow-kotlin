package com.squareup.workflow1.ui.internal.test.compose

import androidx.compose.ui.test.junit4.ComposeContentTestRule

/**
 * This extension is useful when we are not guaranteed synchronous execution of the UI outcomes.
 *
 * We wait for the Composition to finish and the Recomposer to go idle. Then advance by 200 ms as
 * a safe upper bound to allow our timeout to fire. Then wait for idle on the composition again.
 */
public fun ComposeContentTestRule.settleForNextRendering() {
  waitForIdle()
  mainClock.advanceTimeBy(200L)
  waitForIdle()
}
