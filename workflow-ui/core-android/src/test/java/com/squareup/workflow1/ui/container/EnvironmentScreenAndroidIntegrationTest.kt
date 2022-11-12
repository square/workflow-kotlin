package com.squareup.workflow1.ui.container

import android.view.View
import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.visual.AndroidViewFactory
import com.squareup.workflow1.visual.AndroidViewVisualizer
import com.squareup.workflow1.visual.ContextOrContainer.AndroidContext
import com.squareup.workflow1.visual.SequentialVisualFactory
import com.squareup.workflow1.visual.VisualEnvironment
import com.squareup.workflow1.visual.VisualEnvironment.Companion.EMPTY
import com.squareup.workflow1.visual.VisualHolder
import com.squareup.workflow1.visual.leafAndroidViewFactory
import com.squareup.workflow1.visual.widen
import com.squareup.workflow1.visual.withLeafAndroidViewFactory
import org.junit.Test
import org.mockito.kotlin.mock

@OptIn(WorkflowUiExperimentalApi::class)
internal class EnvironmentScreenAndroidIntegrationTest {
  @Test fun mergingWorksForBuild() {
    var lastEnv: VisualEnvironment? = null
    var lastView: View? = null

    val stockLeafFactory = EMPTY.leafAndroidViewFactory

    val altFactory = AndroidViewFactory<WrappedScreen> { _, _, e, _ ->
      lastEnv = e
      VisualHolder(mockView().also { lastView = it }) {}
    }

    val env = EMPTY.withLeafAndroidViewFactory(
      SequentialVisualFactory(listOf(altFactory.widen(), stockLeafFactory))
    ) + (SomeEnvValue to "hi")

    val wrappedScreen = WrappedScreen()
    val envScreen = wrappedScreen.withEnvironment(env)
    val visualizer = AndroidViewVisualizer()
    visualizer.show(envScreen, AndroidContext(mock()), EMPTY) { visual, doFirstUpdate ->
      lastView = visual
      doFirstUpdate()
    }

    // TODO: Right now this only works via the legacy integration
    // The wrapper env is on the view.
    // assertThat(lastView!!.environmentOrNull!![SomeEnvValue]).isEqualTo("hi")

    // The wrapper env reached the inner view factory.
    assertThat(lastEnv!![SomeEnvValue]).isEqualTo("hi")

    // By putting altFactory into the environment in envScreen,
    // we expect it to have built the view for wrappedScreen instead of the hard
    // coded default.
    assertThat(wrappedScreen.viewFactory.lastView).isNull()
    assertThat(visualizer.visual).isSameInstanceAs(lastView)
  }

  // TODO: so far we've stopped providing new env on update
  // @Test fun mergingWorksForUpdate() {
  //   val wrappedScreen = WrappedScreen()
  //   val withEnvironment = wrappedScreen.withEnvironment(EMPTY + (SomeEnvValue to "hi"))
  //   val visualizer = AndroidViewVisualizer()
  //   var lastView: View? = null
  //
  //   visualizer.show(withEnvironment, AndroidContext(mock()), EMPTY) { visual, doFirstUpdate ->
  //     lastView = visual
  //     doFirstUpdate()
  //   }
  //   assertThat(lastView!!.environmentOrNull!![SomeEnvValue]).isEqualTo("hi")
  //
  //   holder.show(wrappedScreen.withEnvironment(EMPTY + (SomeEnvValue to "bye")), EMPTY)
  //
  //   assertThat(wrappedScreen.viewFactory.lastEnv!![SomeEnvValue]).isEqualTo("bye")
  //   assertThat(holder.environment[SomeEnvValue]).isEqualTo("bye")
  // }
}
