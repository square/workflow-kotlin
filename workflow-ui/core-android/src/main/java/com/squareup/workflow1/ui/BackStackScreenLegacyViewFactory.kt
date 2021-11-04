package com.squareup.workflow1.ui

import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import com.squareup.workflow1.ui.container.BackStackContainer
import com.squareup.workflow1.ui.container.BackStackScreen

@WorkflowUiExperimentalApi
internal object BackStackScreenLegacyViewFactory : ViewFactory<BackStackScreen<*>>
by BuilderViewFactory(
  type = BackStackScreen::class,
  viewConstructor = { initialRendering, initialEnv, context, _ ->
    BackStackContainer(context)
      .apply {
        id = R.id.workflow_back_stack_container
        layoutParams = (LayoutParams(MATCH_PARENT, MATCH_PARENT))
        bindShowRendering(initialRendering, initialEnv, ::update)
      }
  }
)
