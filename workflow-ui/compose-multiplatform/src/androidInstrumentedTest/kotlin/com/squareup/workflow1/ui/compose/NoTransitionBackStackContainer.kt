package com.squareup.workflow1.ui.compose

import android.content.Context
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import com.squareup.workflow1.ui.NamedScreen
import com.squareup.workflow1.ui.R
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewHolder
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.navigation.BackStackContainer
import com.squareup.workflow1.ui.navigation.BackStackScreen

/**
 * A subclass of [BackStackContainer] that disables transitions to make it simpler to test the
 * actual backstack logic. Views are just swapped instantly.
 */
// TODO (https://github.com/square/workflow-kotlin/issues/306) Remove once BackStackContainer is
//  transition-ignorant.
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

  companion object : ScreenViewFactory<BackStackScreen<*>>
  by ScreenViewFactory.fromCode(
    buildView = { _, initialEnvironment, context, _ ->
      val view = NoTransitionBackStackContainer(context)
        .apply {
          id = R.id.workflow_back_stack_container
          layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }
      ScreenViewHolder(initialEnvironment, view) { rendering, environment ->
        view.update(rendering, environment)
      }
    }
  )
}
