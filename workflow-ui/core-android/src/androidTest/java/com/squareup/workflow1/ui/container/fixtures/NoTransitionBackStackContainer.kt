package com.squareup.workflow1.ui.container.fixtures

import android.content.Context
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import com.squareup.workflow1.ui.ManualScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.bindShowRendering
import com.squareup.workflow1.ui.container.BackStackContainer
import com.squareup.workflow1.ui.container.BackStackScreen
import com.squareup.workflow1.ui.R

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
  by ManualScreenViewFactory(
    type = BackStackScreen::class,
    viewConstructor = { initialRendering, initialEnv, context, _ ->
      NoTransitionBackStackContainer(context)
        .apply {
          id = R.id.workflow_back_stack_container
          layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)
          bindShowRendering(initialRendering, initialEnv, ::update)
        }
    }
  )
}
