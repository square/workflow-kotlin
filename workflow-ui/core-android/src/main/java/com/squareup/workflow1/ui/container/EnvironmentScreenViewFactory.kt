package com.squareup.workflow1.ui.container

import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewFactory.Companion.fromCode
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.merge
import com.squareup.workflow1.ui.toUnwrappingViewFactory
import com.squareup.workflow1.ui.toViewFactory

@WorkflowUiExperimentalApi
internal fun <WrappedT : Screen> EnvironmentScreenViewFactory():
  ScreenViewFactory<EnvironmentScreen<WrappedT>> {
  return fromCode { initialEnvScreen, initialEnvironment, context, container ->
    val mergedInitialEnvironment = initialEnvironment merge initialEnvScreen.environment

    initialEnvScreen.wrapped.toViewFactory(mergedInitialEnvironment)
      .toUnwrappingViewFactory<EnvironmentScreen<WrappedT>, WrappedT>(
        unwrap = { it.wrapped },
        showWrapperScreen = { _, envScreen, environment, showUnwrapped ->
          showUnwrapped(envScreen.wrapped, environment merge envScreen.environment)
        }
      )
      .buildView(initialEnvScreen, mergedInitialEnvironment, context, container)
  }
}
