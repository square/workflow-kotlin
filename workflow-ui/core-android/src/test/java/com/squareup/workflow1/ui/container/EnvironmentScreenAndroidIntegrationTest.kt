@file:OptIn(WorkflowUiExperimentalApi::class)

package com.squareup.workflow1.ui.container

import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.plus
import com.squareup.workflow1.ui.show
import com.squareup.workflow1.ui.startShowing
import com.squareup.workflow1.ui.toViewFactory
import com.squareup.workflow1.visual.VisualEnvironment.Companion.EMPTY
import org.junit.Test
import org.mockito.kotlin.mock

internal class EnvironmentScreenAndroidIntegrationTest {
  @Test fun mergingWorksForBuild() {
    val altFactory = WrappedFactory()
    val env = EMPTY + (SomeEnvValue to "hi") + ViewRegistry(altFactory)

    val wrappedScreen = WrappedScreen()
    val envScreen = wrappedScreen.withEnvironment(env)
    val holder = envScreen.toViewFactory(EMPTY)
      .startShowing(envScreen, EMPTY, mock())

    // By putting altFactory into the environment in envScreen,
    // we expect it to have built the view for wrappedScreen instead of the hard
    // coded default.
    assertThat(wrappedScreen.viewFactory.lastView).isNull()
    assertThat(holder.view).isSameInstanceAs(altFactory.lastView)

    // The wrapper env reached the inner view factory.
    assertThat(altFactory.lastEnv!![SomeEnvValue]).isEqualTo("hi")
    // The wrapper env is on the view.
    assertThat(holder.environment[SomeEnvValue]).isEqualTo("hi")
  }

  @Test fun mergingWorksForUpdate() {
    val wrappedScreen = WrappedScreen()
    val withEnvironment = wrappedScreen.withEnvironment(EMPTY + (SomeEnvValue to "hi"))
    val holder = withEnvironment.toViewFactory(EMPTY)
      .startShowing(withEnvironment, EMPTY, mock())
    assertThat(holder.environment[SomeEnvValue]).isEqualTo("hi")

    holder.show(wrappedScreen.withEnvironment(EMPTY + (SomeEnvValue to "bye")), EMPTY)

    assertThat(wrappedScreen.viewFactory.lastEnv!![SomeEnvValue]).isEqualTo("bye")
    assertThat(holder.environment[SomeEnvValue]).isEqualTo("bye")
  }
}
