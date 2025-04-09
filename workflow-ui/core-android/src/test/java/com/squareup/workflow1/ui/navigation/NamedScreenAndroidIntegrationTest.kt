package com.squareup.workflow1.ui.navigation

import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.ui.NamedScreen
import com.squareup.workflow1.ui.ViewEnvironment.Companion.EMPTY
import com.squareup.workflow1.ui.show
import com.squareup.workflow1.ui.showing
import com.squareup.workflow1.ui.startShowing
import com.squareup.workflow1.ui.toViewFactory
import org.junit.Test
import org.mockito.kotlin.mock

internal class NamedScreenAndroidIntegrationTest {
  @Test fun buildsOkay() {
    val wrappedScreen = WrappedScreen()
    val named = NamedScreen(wrappedScreen, "fred")

    val holder = named.toViewFactory(EMPTY)
      .startShowing(named, EMPTY, mock())
    assertThat(holder.view).isSameInstanceAs(wrappedScreen.viewFactory.lastView)
    assertThat(holder.showing).isSameInstanceAs(named)
  }

  @Test fun updatesOkay() {
    val wrappedScreen = WrappedScreen()
    val named = NamedScreen(wrappedScreen, "fred")
    val holder = named.toViewFactory(EMPTY)
      .startShowing(named, EMPTY, mock())

    holder.show(NamedScreen(wrappedScreen, "fred"), EMPTY)
    assertThat(holder).isSameInstanceAs(holder)
    assertThat(holder.showing).isNotSameInstanceAs(named)
  }
}
