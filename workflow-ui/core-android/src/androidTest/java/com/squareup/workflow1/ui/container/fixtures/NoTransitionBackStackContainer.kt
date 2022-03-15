package com.squareup.workflow1.ui.container.fixtures

import android.content.Context
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import com.squareup.workflow1.ui.NamedScreen
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
  override fun performTransition(
    oldHolderMaybe: ScreenViewHolder<NamedScreen<*>>?,
    newHolder: ScreenViewHolder<NamedScreen<*>>,
    popped: Boolean
  ) {
    oldHolderMaybe?.view?.let(::removeView)
    addView(newHolder.view)
  }

  companion object : ScreenViewFactory<BackStackScreen<*>> by ScreenViewFactory(
    buildView = { _, context, _ ->
      NoTransitionBackStackContainer(context)
        .apply {
          id = R.id.workflow_back_stack_container
          layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }
    },
    updateView = { view, rendering, environment ->
      (view as NoTransitionBackStackContainer).update(rendering, environment)
    }
  )
}
