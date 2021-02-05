package com.squareup.workflow1.ui.compose

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnDetachedFromWindow
import androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.ui.compose.ComposeTestActivity.TestRendering.ComposeRendering
import com.squareup.workflow1.ui.compose.ComposeTestActivity.TestRendering.EmptyRendering
import org.junit.Rule
import org.junit.Test

internal class ComposeInBackStackTest {

  @Rule @JvmField val composeRule = createAndroidComposeRule<ComposeTestActivity>()
  private val scenario get() = composeRule.activityRule.scenario

  @Test fun compose_view_assertions_work() {
    val firstScreen = ComposeRendering {
      BasicText("First Screen")
    }

    scenario.onActivity {
      it.setBackstack(firstScreen)
    }

    composeRule.onNodeWithText("First Screen").assertIsDisplayed()

    // Navigate away from the first screen.
    scenario.onActivity {
      it.setBackstack(firstScreen, EmptyRendering)
    }

    composeRule.onNodeWithText("First Screen").assertDoesNotExist()
  }

  @Test fun composition_is_disposed_when_navigated_away_dispose_on_detach_strategy() {
    var composedCount = 0
    var disposedCount = 0
    val firstScreen = ComposeRendering(disposeStrategy = DisposeOnDetachedFromWindow) {
      DisposableEffect(Unit) {
        composedCount++
        onDispose {
          disposedCount++
        }
      }
    }

    scenario.onActivity {
      it.setBackstack(firstScreen)
    }

    assertThat(composedCount).isEqualTo(1)
    assertThat(disposedCount).isEqualTo(0)

    // Navigate away.
    scenario.onActivity {
      it.setBackstack(firstScreen, EmptyRendering)
    }

    assertThat(composedCount).isEqualTo(1)
    assertThat(disposedCount).isEqualTo(1)
  }

  @Test fun composition_is_disposed_when_navigated_away_dispose_on_destroy_strategy() {
    var composedCount = 0
    var disposedCount = 0
    val firstScreen = ComposeRendering(disposeStrategy = DisposeOnViewTreeLifecycleDestroyed) {
      DisposableEffect(Unit) {
        composedCount++
        onDispose {
          disposedCount++
        }
      }
    }

    scenario.onActivity {
      it.setBackstack(firstScreen)
    }

    assertThat(composedCount).isEqualTo(1)
    assertThat(disposedCount).isEqualTo(0)

    // Navigate away.
    scenario.onActivity {
      it.setBackstack(firstScreen, EmptyRendering)
    }

    assertThat(composedCount).isEqualTo(1)
    assertThat(disposedCount).isEqualTo(1)
  }
}
