package com.squareup.workflow1.ui.container

import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.merge
import com.squareup.workflow1.ui.toViewFactory

@WorkflowUiExperimentalApi
internal fun EnvironmentScreenViewFactory(
  initialRendering: EnvironmentScreen<*>,
  initialViewEnvironment: ViewEnvironment
): ScreenViewFactory<EnvironmentScreen<*>> {
  val realFactory = initialRendering.screen.toViewFactory(
    initialViewEnvironment merge initialRendering.viewEnvironment
  )

  return ScreenViewFactory(
    buildView = realFactory::buildView,
    updateView = { view, rendering, environment ->
      realFactory.updateView(view, rendering.screen, environment)
    }
  )
}
