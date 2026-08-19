package com.squareup.sample.compose.presenterdemo.backstack

import androidx.compose.runtime.Composable
import com.squareup.workflow1.presenter.Presenter
import com.squareup.workflow1.presenter.PresenterModifier
import com.squareup.workflow1.presenter.PresenterModifier.Companion

/**
 * Emits a back stack that is managed by what child nodes [content] emits. To "navigate forward",
 * compose an additional presenter after composing previous presenters. If you wish a view model
 * to remain on the stack, you must continue to compose it. To remove a view model from the stack,
 * either at the end or somewhere in the middle, just stop composing it.
 */
@Composable
fun BackStackPresenter(
  onBack: (() -> Unit)?,
  modifier: PresenterModifier = Companion,
  content: @Composable () -> Unit
) {
  Presenter(
    modifier = modifier,
    viewModelProducer = { children ->
      BackStackScreen(
        entries = children,
        onBack = onBack,
      )
    },
    content = content
  )
}
