package com.squareup.workflow1.ui.container

import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import com.squareup.workflow1.ui.R
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

@WorkflowUiExperimentalApi
internal object BackStackScreenViewFactory : ScreenViewFactory<BackStackScreen<*>>
by ScreenViewFactory(
  buildView = { _, context, _ ->
    BackStackContainer(context)
      .apply {
        id = R.id.workflow_back_stack_container
        layoutParams = (LayoutParams(MATCH_PARENT, MATCH_PARENT))
      }
  },
  updateView = { view, rendering, environment ->
    (view as BackStackContainer).update(rendering, environment)
  }
)
