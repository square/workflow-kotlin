@file:OptIn(WorkflowUiExperimentalApi::class)

package com.squareup.workflow1.ui.container

import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.ui.ViewEnvironment.Companion.EMPTY
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.buildView
import com.squareup.workflow1.ui.environment
import com.squareup.workflow1.ui.getRendering
import com.squareup.workflow1.ui.plus
import com.squareup.workflow1.ui.showRendering
import com.squareup.workflow1.ui.start
import org.junit.Test
import org.mockito.kotlin.mock

internal class EnvironmentScreenAndroidIntegrationTest {
  @Test fun mergingWorksForBuild() {
    // By putting altFactory into the environment in envScreen,
    // we expect it to build the view for wrappedScreen instead of the hard
    // coded default, wrappedScreen.viewFactory
    val altFactory = WrappedFactory()
    val env = EMPTY + (SomeEnvValue to "hi") + ViewRegistry(altFactory)

    val wrappedScreen = WrappedScreen()
    val envScreen = wrappedScreen.withEnvironment(env)
    val view = envScreen.buildView(EMPTY, mock())

    assertThat(wrappedScreen.viewFactory.lastView).isNull()

    // altFactory made the view.
    assertThat(view).isSameInstanceAs(altFactory.lastView)

    // The wrapper env reached the inner view factory.
    assertThat(altFactory.lastEnv!![SomeEnvValue]).isEqualTo("hi")
    // The wrapper env is on the view.
    assertThat(view.environment!![SomeEnvValue]).isEqualTo("hi")
    // The wrapper rendering is on the view.
    assertThat(view.getRendering<Any>()).isEqualTo(envScreen)
  }

  @Test fun mergingWorksForUpdate() {
    val wrappedScreen = WrappedScreen()
    val view = wrappedScreen.withEnvironment(EMPTY + (SomeEnvValue to "hi"))
      .buildView(EMPTY, mock())
    assertThat(view.environment!![SomeEnvValue]).isEqualTo("hi")

    view.start()
    view.showRendering(wrappedScreen.withEnvironment(EMPTY + (SomeEnvValue to "bye")), EMPTY)

    assertThat(wrappedScreen.viewFactory.lastEnv!![SomeEnvValue]).isEqualTo("bye")

    // TODO To be fixed or obviated by https://github.com/square/workflow-kotlin/pull/703
    // assertThat(view.environment!![SomeEnvValue]).isEqualTo("bye")
  }
}
