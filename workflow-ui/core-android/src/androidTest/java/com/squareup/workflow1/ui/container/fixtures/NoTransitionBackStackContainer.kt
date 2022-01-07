package com.squareup.workflow1.ui.container.fixtures

import android.content.Context
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import com.squareup.workflow1.ui.R
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewHolder
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.container.BackStackContainer
import com.squareup.workflow1.ui.container.BackStackScreen

/**
 * A subclass of [BackStackContainer] that disables transitions to make it simpler to test the
 * actual backstack logic. Views are just swapped instantly.
 */
@OptIn(WorkflowUiExperimentalApi::class)
internal class NoTransitionBackStackContainer(context: Context) : BackStackContainer(context) {

  override fun performTransition(oldViewMaybe: View?, newView: View, popped: Boolean) {
    oldViewMaybe?.let(::removeView)
    addView(newView)
  }

  companion object : ScreenViewFactory<BackStackScreen<*>>
  by ScreenViewFactory.of(
    viewConstructor = { initialRendering, initialEnv, context, _ ->
      NoTransitionBackStackContainer(context)
        .let { view ->
          view.id = R.id.workflow_back_stack_container
          view.layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)
          ScreenViewHolder(initialRendering, initialEnv, view, view::update)
        }
    }
  )
}
