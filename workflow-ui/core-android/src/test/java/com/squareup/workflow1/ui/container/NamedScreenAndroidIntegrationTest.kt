@file:OptIn(WorkflowUiExperimentalApi::class)

package com.squareup.workflow1.ui.container

import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.ui.NamedScreen
import com.squareup.workflow1.ui.ViewEnvironment.Companion.EMPTY
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.buildView
import com.squareup.workflow1.ui.getRendering
import com.squareup.workflow1.ui.showRendering
import com.squareup.workflow1.ui.start
import org.junit.Test
import org.mockito.kotlin.mock

internal class NamedScreenAndroidIntegrationTest {
  @Test fun buildsOkay() {
    val wrappedScreen = WrappedScreen()
    val named = NamedScreen(wrappedScreen, "fred")

    val view = named.buildView(EMPTY, mock())
    assertThat(view).isSameInstanceAs(wrappedScreen.viewFactory.lastView)
    assertThat(view.getRendering<Any>()).isSameInstanceAs(named)
  }

  @Test fun updatesOkay() {
    val wrappedScreen = WrappedScreen()
    val named = NamedScreen(wrappedScreen, "fred")

    val view = named.buildView(EMPTY, mock())
    view.start()

    view.showRendering(NamedScreen(wrappedScreen, "fred"), EMPTY)
    assertThat(view).isSameInstanceAs(view)
    assertThat(view.getRendering<Any>()).isNotSameInstanceAs(named)
  }
}
