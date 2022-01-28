package com.squareup.workflow1.ui.compose

import android.content.Context
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import com.squareup.workflow1.ui.BuilderViewFactory
import com.squareup.workflow1.ui.ViewFactory
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.backstack.BackStackContainer
import com.squareup.workflow1.ui.backstack.BackStackScreen
import com.squareup.workflow1.ui.bindShowRendering
import com.squareup.workflow1.ui.container.R

/**
 * A subclass of [BackStackContainer] that disables transitions to make it simpler to test the
 * actual backstack logic. Views are just swapped instantly.
 */
// TODO (https://github.com/square/workflow-kotlin/issues/306) Remove once BackStackContainer is
//  transition-ignorant.
@OptIn(WorkflowUiExperimentalApi::class)
internal class NoTransitionBackStackContainer(context: Context) : BackStackContainer(context) {

  override fun performTransition(oldViewMaybe: View?, newView: View, popped: Boolean) {
    oldViewMaybe?.let(::removeView)
    addView(newView)
  }

  companion object : ViewFactory<BackStackScreen<*>>
  by BuilderViewFactory(
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
