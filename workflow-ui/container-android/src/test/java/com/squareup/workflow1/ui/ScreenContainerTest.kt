@file:OptIn(WorkflowUiExperimentalApi::class)

package com.squareup.workflow1.ui

import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.ui.container.BackStackScreen
import com.squareup.workflow1.ui.container.EnvironmentScreen
import com.squareup.workflow1.ui.container.withEnvironment
import org.junit.Test

internal class ScreenContainerTest {
  object MyScreen : Screen

  @Test
  fun deepMapRecurses() {
    val backStack = BackStackScreen(NamedScreen(MyScreen, "name"))
    @Suppress("UNCHECKED_CAST")
    val mappedBackStack = backStack
      .deepMap { it.withEnvironment() } as BackStackScreen<NamedScreen<EnvironmentScreen<MyScreen>>>

    assertThat(mappedBackStack.top.content.content).isSameInstanceAs(MyScreen)
  }
}
