package com.squareup.workflow1.ui.navigation

import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import com.squareup.workflow1.ui.R
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewHolder
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

@WorkflowUiExperimentalApi
internal object BackStackScreenViewFactory : ScreenViewFactory<BackStackScreen<*>>
by ScreenViewFactory.fromCode(
  buildView = { _, initialEnvironment, context, _ ->
    BackStackContainer(context)
      .let { view ->
        view.id = R.id.workflow_back_stack_container
        view.layoutParams = (LayoutParams(MATCH_PARENT, MATCH_PARENT))
        ScreenViewHolder(initialEnvironment, view) { rendering, environment ->
          view.update(rendering, environment)
        }
      }
  }
)
