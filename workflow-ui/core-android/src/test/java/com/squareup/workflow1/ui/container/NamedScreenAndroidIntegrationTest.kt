@file:OptIn(WorkflowUiExperimentalApi::class)

package com.squareup.workflow1.ui.container

import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.ui.NamedScreen
import com.squareup.workflow1.ui.ViewEnvironment.Companion.EMPTY
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.buildView
import com.squareup.workflow1.ui.show
import com.squareup.workflow1.ui.showing
import org.junit.Test
import org.mockito.kotlin.mock

internal class NamedScreenAndroidIntegrationTest {
  @Test fun buildsOkay() {
    val wrappedScreen = WrappedScreen()
    val named = NamedScreen(wrappedScreen, "fred")

    val holder = named.buildView(EMPTY, mock())
    assertThat(holder.view).isSameInstanceAs(wrappedScreen.viewFactory.lastView)
    assertThat(holder.showing).isSameInstanceAs(named)
  }

  @Test fun updatesOkay() {
    val wrappedScreen = WrappedScreen()
    val named = NamedScreen(wrappedScreen, "fred")
    val holder = named.buildView(EMPTY, mock())

    holder.show(NamedScreen(wrappedScreen, "fred"), EMPTY)
    assertThat(holder).isSameInstanceAs(holder)
    assertThat(holder.showing).isNotSameInstanceAs(named)
  }
}
